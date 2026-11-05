# Navigation Map — in-scope surfaces
# Bản đồ điều hướng (phạm vi đã duyệt)

Evidence: `code_verified` unless marked *(observed)*.

```mermaid
flowchart TD
    START([Startup]) --> SPLASH[Splash 2s min]
    SPLASH --> SHELL[SCR-SHELL-001<br/>Window shell + title bar + menu]
    SHELL --> MAIN[SCR-MAIN-001 Main workspace]

    MAIN --> EXP[SCR-MAIN-002 Object Explorer]
    MAIN --> ED[SCR-MAIN-003 Editor workspace]
    MAIN --> RES[SCR-MAIN-004 Result panel]
    MAIN --> SB[SCR-MAIN-005 Status bars]

    SHELL -- "Settings menu / gear" --> SET[SCR-SET-001 Settings window]
    SHELL -- "Format ▸ SQL Style Manager" --> STYLE[SCR-DLG-009 Code style manager]
    SET -- "Editor ▸ SQL Code Styles" --> STYLE
    SHELL -- "Help ▸ About" --> ABOUT[SCR-DLG-012 About]
    SHELL -- "Help ▸ Check updates / update badge" --> UPD[SCR-DLG-013 Update]

    EXP -- "Connect button / Edit Connection (observed)" --> CONN[SCR-DLG-001 Connection]
    EXP -- "Connect profile without password" --> PWD[SCR-DLG-002 Reconnect password]
    EXP -- "New / Rename Group (observed)" --> GRP[SCR-DLG-003 Group input]
    EXP -- "Delete Group" --> GDEL[SCR-DLG-004 Group delete confirm]
    EXP -- "Filter DB" --> DBF[SCR-DLG-005 Database filter]
    EXP -- "Filter on folder node" --> OBF[SCR-DLG-006 Object filter]
    ED -- "Banner: Kết nối (observed)" --> PWD

    ED -- "Run dangerous SQL" --> GUARD[SCR-DLG-007 SQL guard]
    ED -- "Format" --> FMT[SCR-DLG-008 Format confirm]
    ED -- "Run with debug markers" --> DBG[SCR-DLG-010 Debug ext warning]
    ED -- "Run" --> RES

    MAIN -. "many actions" .-> POP[SCR-DLG-011 Message popup]
    MAIN -. "long operations" .-> LOAD[SCR-DLG-014 Loading overlay]
```

## Out-of-scope exits (recorded, `deferred`)

| From | Control | Opens |
|---|---|---|
| Navbar | Snippets · Sheets · BRAVO Tool | Snippets manager · Google Sheets window · BRAVO window |
| View menu | Object search · Query History · Compare results · Activity Monitor · Schema Compare · Google Sheets · Report Gen | tabs / sub-windows |
| Action bar | Plan · Compare · Syntax reference · History · File history · Object search · Import · Export · Split | feature views |
| Explorer context menu | SQL Trace Profiler · Activity Monitor · So sánh Schema · Design / Edit data / Dependencies … | sub-windows / tabs |
| Result panel | So sánh (compare results) | Result comparator |
