package com.lltool.core.application.dependency;

import com.lltool.core.domain.dependency.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for constructing, laying out, and analyzing database object dependency graphs.
 */
public class DependencyGraphService {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphService.class);

    private static final double LAYER_SPACING_X = 320.0;
    private static final double NODE_SPACING_Y = 120.0;
    private static final double ORIGIN_X = 420.0;
    private static final double ORIGIN_Y = 250.0;

    /**
     * Calculates hierarchical Sugiyama-style coordinates for nodes in the graph.
     * Layers are organized horizontally (Left to Right):
     *   Negative layers (<0): Upstream dependencies (tables, views needed by root).
     *   Layer 0: Root target object(s).
     *   Positive layers (>0): Downstream dependents (views, procs, triggers that depend on root).
     */
    public void calculateHierarchicalLayout(DependencyGraphModel model) {
        if (model == null || model.getNodeCount() == 0) return;

        DependencyNode rootNode = model.getRootNode();
        if (rootNode == null) return;

        // 1. Assign layers via BFS
        Map<String, Integer> layers = new HashMap<>();
        layers.put(rootNode.getId(), 0);

        // BFS Downstream (Dependents: source -> target)
        Queue<String> forwardQueue = new LinkedList<>();
        forwardQueue.add(rootNode.getId());
        while (!forwardQueue.isEmpty()) {
            String currentId = forwardQueue.poll();
            int currentLayer = layers.getOrDefault(currentId, 0);

            for (DependencyEdge edge : model.getOutgoingEdges(currentId)) {
                String targetId = edge.getTargetId();
                if (!layers.containsKey(targetId)) {
                    layers.put(targetId, currentLayer + 1);
                    forwardQueue.add(targetId);
                }
            }
        }

        // BFS Upstream (Dependencies: target -> source)
        Queue<String> reverseQueue = new LinkedList<>();
        reverseQueue.add(rootNode.getId());
        while (!reverseQueue.isEmpty()) {
            String currentId = reverseQueue.poll();
            int currentLayer = layers.getOrDefault(currentId, 0);

            for (DependencyEdge edge : model.getIncomingEdges(currentId)) {
                String sourceId = edge.getSourceId();
                if (!layers.containsKey(sourceId)) {
                    layers.put(sourceId, currentLayer - 1);
                    reverseQueue.add(sourceId);
                }
            }
        }

        // 2. Group nodes by layer
        Map<Integer, List<DependencyNode>> layerBuckets = new TreeMap<>();
        for (DependencyNode node : model.getNodes().values()) {
            int layer = layers.getOrDefault(node.getId(), 0);
            node.setLayer(layer);
            layerBuckets.computeIfAbsent(layer, k -> new ArrayList<>()).add(node);
        }

        // 2.1. Reduce edge crossings using 2-pass barycenter heuristic (DG-5)
        reduceCrossingsBarycenter(model, layerBuckets);

        // 3. Position nodes on (x, y) coordinates
        for (Map.Entry<Integer, List<DependencyNode>> entry : layerBuckets.entrySet()) {
            int layer = entry.getKey();
            List<DependencyNode> nodesInLayer = entry.getValue();

            double x = ORIGIN_X + (layer * LAYER_SPACING_X);
            double totalHeight = (nodesInLayer.size() - 1) * NODE_SPACING_Y;
            double startY = ORIGIN_Y - (totalHeight / 2.0);

            for (int i = 0; i < nodesInLayer.size(); i++) {
                DependencyNode node = nodesInLayer.get(i);
                double y = startY + (i * NODE_SPACING_Y);
                node.setX(x);
                node.setY(y);
            }
        }
    }

    private void reduceCrossingsBarycenter(DependencyGraphModel model, Map<Integer, List<DependencyNode>> layerBuckets) {
        if (layerBuckets == null || layerBuckets.size() <= 1) return;

        List<Integer> layerIndices = new ArrayList<>(layerBuckets.keySet());
        Collections.sort(layerIndices);

        // Forward pass: from second layer to last
        for (int i = 1; i < layerIndices.size(); i++) {
            int layer = layerIndices.get(i);
            int prevLayer = layerIndices.get(i - 1);
            List<DependencyNode> nodes = layerBuckets.get(layer);
            List<DependencyNode> prevNodes = layerBuckets.get(prevLayer);
            Map<String, Integer> prevNodePos = new HashMap<>();
            for (int p = 0; p < prevNodes.size(); p++) {
                prevNodePos.put(prevNodes.get(p).getId(), p);
            }

            Map<DependencyNode, Double> barycenters = new HashMap<>();
            for (int p = 0; p < nodes.size(); p++) {
                DependencyNode node = nodes.get(p);
                List<DependencyEdge> inEdges = model.getIncomingEdges(node.getId());
                double sum = 0;
                int count = 0;
                for (DependencyEdge edge : inEdges) {
                    Integer pos = prevNodePos.get(edge.getSourceId());
                    if (pos != null) {
                        sum += pos;
                        count++;
                    }
                }
                barycenters.put(node, count > 0 ? (sum / count) : (double) p);
            }

            nodes.sort(Comparator.comparingDouble(barycenters::get));
        }

        // Backward pass: from second to last layer down to first
        for (int i = layerIndices.size() - 2; i >= 0; i--) {
            int layer = layerIndices.get(i);
            int nextLayer = layerIndices.get(i + 1);
            List<DependencyNode> nodes = layerBuckets.get(layer);
            List<DependencyNode> nextNodes = layerBuckets.get(nextLayer);
            Map<String, Integer> nextNodePos = new HashMap<>();
            for (int p = 0; p < nextNodes.size(); p++) {
                nextNodePos.put(nextNodes.get(p).getId(), p);
            }

            Map<DependencyNode, Double> barycenters = new HashMap<>();
            for (int p = 0; p < nodes.size(); p++) {
                DependencyNode node = nodes.get(p);
                List<DependencyEdge> outEdges = model.getOutgoingEdges(node.getId());
                double sum = 0;
                int count = 0;
                for (DependencyEdge edge : outEdges) {
                    Integer pos = nextNodePos.get(edge.getTargetId());
                    if (pos != null) {
                        sum += pos;
                        count++;
                    }
                }
                barycenters.put(node, count > 0 ? (sum / count) : (double) p);
            }

            nodes.sort(Comparator.comparingDouble(barycenters::get));
        }
    }

    /**
     * Generates sequential suggested steps (Impact Analysis) for altering/dropping a target.
     */
    public List<SuggestedStep> generateSuggestedSteps(DependencyGraphModel model, String targetId) {
        List<SuggestedStep> steps = new ArrayList<>();
        if (model == null) return steps;

        DependencyNode target = model.getNode(targetId);
        if (target == null) target = model.getRootNode();
        if (target == null) return steps;

        int stepNum = 1;

        // Collect all downstream dependents reachable from target
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        List<DependencyNode> dependents = new ArrayList<>();

        queue.add(target.getId());
        visited.add(target.getId());

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            for (DependencyEdge edge : model.getOutgoingEdges(currId)) {
                String depId = edge.getTargetId();
                if (!visited.contains(depId)) {
                    visited.add(depId);
                    DependencyNode depNode = model.getNode(depId);
                    if (depNode != null) {
                        dependents.add(depNode);
                        queue.add(depId);
                    }
                }
            }
        }

        // Step 1: Dismantling in reverse layer order (deepest dependents first)
        List<DependencyNode> dismantleList = new ArrayList<>(dependents);
        dismantleList.sort(Comparator.comparingInt(DependencyNode::getLayer).reversed());

        for (DependencyNode dep : dismantleList) {
            if (dep.getType() == DependencyNodeType.VIEW || dep.getType() == DependencyNodeType.MATERIALIZED_VIEW) {
                steps.add(new SuggestedStep(
                        stepNum++,
                        "BACKUP_OR_DROP_VIEW",
                        dep.getDisplayName(),
                        "Sao lưu và tạm thời hủy/vô hiệu hóa view trước khi sửa đổi cấu trúc",
                        "DROP VIEW IF EXISTS " + dep.getSchema() + "." + dep.getName() + ";"
                ));
            } else if (dep.getType() == DependencyNodeType.TRIGGER) {
                steps.add(new SuggestedStep(
                        stepNum++,
                        "DISABLE_TRIGGER",
                        dep.getDisplayName(),
                        "Tạm thời tắt trigger liên quan",
                        "DISABLE TRIGGER " + dep.getName() + " ON " + target.getSchema() + "." + target.getName() + ";"
                ));
            }
        }

        // Step 2: The actual alteration on the target
        if (target.isColumn()) {
            steps.add(new SuggestedStep(
                    stepNum++,
                    "ALTER_COLUMN",
                    target.getDisplayName(),
                    "Thực hiện thay đổi kiểu dữ liệu hoặc cấu trúc trên cột mục tiêu",
                    "-- TODO: Áp dụng thay đổi cấu trúc trên cột " + target.getDisplayName() + "\n-- Ví dụ: ALTER TABLE " + target.getSchema() + "." + target.getName() + " ALTER COLUMN " + target.getColumnName() + " <NEW_TYPE>;"
            ));
        } else {
            steps.add(new SuggestedStep(
                    stepNum++,
                    "MODIFY_TABLE",
                    target.getDisplayName(),
                    "Thực hiện áp dụng các thay đổi DDL trên bảng mục tiêu",
                    "-- TODO: Áp dụng thay đổi cấu trúc bảng: " + target.getDisplayName() + "\n-- (Nhập câu lệnh ALTER / DROP / RENAME của bạn tại đây)"
            ));
        }

        // Step 3: Recreation in forward layer order (closest dependents first)
        List<DependencyNode> recreateList = new ArrayList<>(dependents);
        recreateList.sort(Comparator.comparingInt(DependencyNode::getLayer));

        for (DependencyNode dep : recreateList) {
            if (dep.getType() == DependencyNodeType.VIEW || dep.getType() == DependencyNodeType.MATERIALIZED_VIEW) {
                String ddl = dep.getDefinitionSql();
                String sql;
                String desc;
                if (ddl != null && !ddl.isBlank()) {
                    sql = ddl;
                    desc = "Tạo lại view với định nghĩa ban đầu";
                } else {
                    sql = "-- Không đọc được định nghĩa của " + dep.getDisplayName();
                    desc = "Không đọc được định nghĩa của " + dep.getDisplayName() + " (cần lấy lại thủ công)";
                }
                steps.add(new SuggestedStep(
                        stepNum++,
                        "RECREATE_VIEW",
                        dep.getDisplayName(),
                        desc,
                        sql
                ));
            } else if (dep.getType() == DependencyNodeType.TRIGGER) {
                steps.add(new SuggestedStep(
                        stepNum++,
                        "ENABLE_TRIGGER",
                        dep.getDisplayName(),
                        "Bật lại trigger sau khi hoàn tất cập nhật",
                        "ENABLE TRIGGER " + dep.getName() + " ON " + target.getSchema() + "." + target.getName() + ";"
                ));
            }
        }

        return steps;
    }

    /**
     * Builds a comprehensive sample graph matching the approved mockup for testing and previewing.
     */
    public DependencyGraphModel createSampleOrdersGraph(GraphDirection direction, int depth) {
        String rootId = "dbo.orders";
        DependencyGraphModel model = new DependencyGraphModel(rootId, direction, depth);

        // Root Node
        DependencyNode root = DependencyNode.of("dbo", "orders", DependencyNodeType.TABLE, true);
        root.setRowCount(89210L);
        root.setDescription("Bảng đơn hàng trung tâm (Root Target)");
        root.setDefinitionSql("""
                CREATE TABLE dbo.orders (
                    order_id INT IDENTITY(1,1) PRIMARY KEY,
                    customer_id INT NOT NULL,
                    order_date DATETIME NOT NULL DEFAULT GETDATE(),
                    total_amount DECIMAL(18,2) NOT NULL,
                    status VARCHAR(20) NOT NULL
                );
                """);
        model.addNode(root);

        // Upstream Node: customers
        if (direction == GraphDirection.BOTH || direction == GraphDirection.DEPENDENCIES) {
            DependencyNode cust = DependencyNode.of("dbo", "customers", DependencyNodeType.TABLE, false);
            cust.setRowCount(12450L);
            cust.setDescription("Bảng khách hàng chính cung cấp ID cho khóa ngoại");
            cust.setDefinitionSql("""
                    CREATE TABLE dbo.customers (
                        customer_id INT IDENTITY(1,1) PRIMARY KEY,
                        customer_name NVARCHAR(100) NOT NULL,
                        email VARCHAR(100) NOT NULL
                    );
                    """);
            model.addNode(cust);

            model.addEdge(new DependencyEdge(
                    cust.getId(), root.getId(),
                    RelationType.FOREIGN_KEY_TO, DependencyType.DIRECT, Confidence.CONFIRMED,
                    "FK_orders_customer"
            ));
        }

        // Downstream Nodes (Dependents)
        if (direction == GraphDirection.BOTH || direction == GraphDirection.DEPENDENTS) {
            // View 1: v_customer_orders
            DependencyNode vOrders = DependencyNode.of("dbo", "v_customer_orders", DependencyNodeType.VIEW, false);
            vOrders.setDescription("View tổng hợp thông tin đơn hàng và tên khách hàng");
            vOrders.setDefinitionSql("""
                    CREATE VIEW dbo.v_customer_orders AS
                    SELECT o.order_id, o.order_date, c.customer_name, o.total_amount
                    FROM dbo.orders o
                    JOIN dbo.customers c ON o.customer_id = c.customer_id;
                    """);
            model.addNode(vOrders);
            model.addEdge(new DependencyEdge(
                    root.getId(), vOrders.getId(),
                    RelationType.REFERENCES_VIEW, DependencyType.DIRECT, Confidence.CONFIRMED,
                    "SELECT"
            ));

            // View 2: v_monthly_revenue
            DependencyNode vRev = DependencyNode.of("dbo", "v_monthly_revenue", DependencyNodeType.VIEW, false);
            vRev.setDescription("View báo cáo doanh thu theo tháng gom nhóm");
            vRev.setDefinitionSql("""
                    CREATE VIEW dbo.v_monthly_revenue AS
                    SELECT YEAR(order_date) AS yr, MONTH(order_date) AS mo, SUM(total_amount) AS revenue
                    FROM dbo.orders
                    GROUP BY YEAR(order_date), MONTH(order_date);
                    """);
            model.addNode(vRev);
            model.addEdge(new DependencyEdge(
                    root.getId(), vRev.getId(),
                    RelationType.REFERENCES_VIEW, DependencyType.DIRECT, Confidence.CONFIRMED,
                    "GROUP BY"
            ));

            // Procedure: usp_close_period
            DependencyNode spClose = DependencyNode.of("dbo", "usp_close_period", DependencyNodeType.PROCEDURE, false);
            spClose.setDescription("Thủ tục chốt sổ đơn hàng định kỳ");
            spClose.setDefinitionSql("""
                    CREATE PROCEDURE dbo.usp_close_period
                    AS
                    BEGIN
                        SET NOCOUNT ON;
                        UPDATE dbo.orders SET status = 'CLOSED' WHERE order_date < DATEADD(month, -1, GETDATE());
                    END;
                    """);
            model.addNode(spClose);
            model.addEdge(new DependencyEdge(
                    root.getId(), spClose.getId(),
                    RelationType.DEPENDS_ON, DependencyType.DIRECT, Confidence.CONFIRMED,
                    "UPDATE DML"
            ));

            // Trigger: trg_orders_audit
            DependencyNode trgAudit = DependencyNode.of("dbo", "trg_orders_audit", DependencyNodeType.TRIGGER, false);
            trgAudit.setDescription("Trigger ghi log kiểm toán khi sửa đơn hàng");
            trgAudit.setDefinitionSql("""
                    CREATE TRIGGER dbo.trg_orders_audit
                    ON dbo.orders AFTER INSERT, UPDATE
                    AS
                    BEGIN
                        INSERT INTO dbo.orders_audit (order_id, modified_date)
                        SELECT order_id, GETDATE() FROM inserted;
                    END;
                    """);
            model.addNode(trgAudit);
            model.addEdge(new DependencyEdge(
                    root.getId(), trgAudit.getId(),
                    RelationType.HAS_TRIGGER, DependencyType.DIRECT, Confidence.CONFIRMED,
                    "AFTER INSERT/UPDATE"
            ));

            // Depth 2 downstream:
            if (depth >= 2) {
                DependencyNode vAnnual = DependencyNode.of("dbo", "v_annual_summary", DependencyNodeType.VIEW, false);
                vAnnual.setDescription("View tổng kết năm dựa trên v_monthly_revenue");
                vAnnual.setDefinitionSql("""
                        CREATE VIEW dbo.v_annual_summary AS
                        SELECT yr, SUM(revenue) AS total_annual_revenue
                        FROM dbo.v_monthly_revenue
                        GROUP BY yr;
                        """);
                model.addNode(vAnnual);
                model.addEdge(new DependencyEdge(
                        vRev.getId(), vAnnual.getId(),
                        RelationType.REFERENCES_VIEW, DependencyType.INDIRECT, Confidence.CONFIRMED,
                        "SELECT"
                ));
            }
        }

        // Layout nodes
        calculateHierarchicalLayout(model);

        // Generate suggested steps
        List<SuggestedStep> steps = generateSuggestedSteps(model, rootId);
        for (SuggestedStep step : steps) {
            model.addSuggestedStep(step);
        }

        return model;
    }
}
