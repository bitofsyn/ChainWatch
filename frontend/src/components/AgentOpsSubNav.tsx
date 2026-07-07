interface AgentOpsSubNavProps {
  active: "board" | "activity";
}

const ITEMS: { key: AgentOpsSubNavProps["active"]; path: string; label: string }[] = [
  { key: "board", path: "/agents", label: "팀 보드" },
  { key: "activity", path: "/agents/activity", label: "핸드오프 로그" }
];

export function AgentOpsSubNav({ active }: AgentOpsSubNavProps) {
  return (
    <nav className="sub-nav" aria-label="Agent 콘솔 메뉴">
      {ITEMS.map((item) => (
        <a
          key={item.key}
          href={`#${item.path}`}
          className={`nav-link ${active === item.key ? "active" : ""}`}
        >
          {item.label}
        </a>
      ))}
    </nav>
  );
}
