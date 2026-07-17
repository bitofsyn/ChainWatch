import { startTransition, useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { ApiError, createUser, fetchUsers, resetUserPassword, updateUser } from "../../api";
import type { Role, UserAccountItem } from "../../types";
import { useAuth } from "../../contexts/AuthContext";
import { formatFullDate } from "../../lib/format";
import { DataState } from "../../components/DataState";

const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "관리자",
  ANALYST: "분석가"
};

/** 생성/초기화 직후 1회만 노출되는 임시 비밀번호 안내 상태. */
interface IssuedPassword {
  username: string;
  password: string;
}

export function AdminUsersSection() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<UserAccountItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [forbidden, setForbidden] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [issuedPassword, setIssuedPassword] = useState<IssuedPassword | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  // 생성 폼
  const [newUsername, setNewUsername] = useState("");
  const [newRole, setNewRole] = useState<Role>("ANALYST");
  const [newDisplayName, setNewDisplayName] = useState("");
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchUsers();
      startTransition(() => {
        setUsers(data);
        setForbidden(false);
        setError(null);
        setLoading(false);
      });
    } catch (cause) {
      startTransition(() => {
        if (cause instanceof ApiError && (cause.status === 403 || cause.status === 401)) {
          setForbidden(true);
          setError(null);
        } else {
          setError("사용자 목록 조회에 실패했습니다. 백엔드 상태를 확인해주세요.");
        }
        setLoading(false);
      });
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault();
    setCreating(true);
    setActionError(null);
    setIssuedPassword(null);
    try {
      const result = await createUser({
        username: newUsername.trim(),
        role: newRole,
        displayName: newDisplayName.trim() || undefined
      });
      if (result.initialPassword) {
        setIssuedPassword({ username: result.user.username, password: result.initialPassword });
      }
      setNewUsername("");
      setNewDisplayName("");
      setNewRole("ANALYST");
      await load();
    } catch (cause) {
      setActionError(
        cause instanceof ApiError && cause.serverMessage
          ? cause.serverMessage
          : "사용자 생성에 실패했습니다."
      );
    } finally {
      setCreating(false);
    }
  };

  const runRowAction = async (id: number, action: () => Promise<unknown>) => {
    setBusyId(id);
    setActionError(null);
    try {
      await action();
      await load();
    } catch (cause) {
      setActionError(
        cause instanceof ApiError && cause.serverMessage
          ? cause.serverMessage
          : "요청 처리에 실패했습니다."
      );
    } finally {
      setBusyId(null);
    }
  };

  const handleToggleActive = (row: UserAccountItem) => {
    if (row.active && !window.confirm(`${row.username} 계정을 비활성화할까요? 모든 세션이 종료됩니다.`)) {
      return;
    }
    runRowAction(row.id, () => updateUser(row.id, { active: !row.active }));
  };

  const handleRoleChange = (row: UserAccountItem, role: Role) => {
    if (role === row.role) {
      return;
    }
    runRowAction(row.id, () => updateUser(row.id, { role }));
  };

  const handlePasswordReset = async (row: UserAccountItem) => {
    if (!window.confirm(`${row.username}의 비밀번호를 초기화할까요? 기존 세션이 모두 종료됩니다.`)) {
      return;
    }
    setBusyId(row.id);
    setActionError(null);
    setIssuedPassword(null);
    try {
      const result = await resetUserPassword(row.id);
      if (result.initialPassword) {
        setIssuedPassword({ username: row.username, password: result.initialPassword });
      }
      await load();
    } catch (cause) {
      setActionError(
        cause instanceof ApiError && cause.serverMessage
          ? cause.serverMessage
          : "비밀번호 초기화에 실패했습니다."
      );
    } finally {
      setBusyId(null);
    }
  };

  if (forbidden) {
    return (
      <DataState
        unauthorized
        unauthorizedMessage="사용자 관리는 ADMIN 권한 계정만 사용할 수 있습니다. 현재 계정 권한을 확인해주세요."
      />
    );
  }

  return (
    <>
      <p className="hint-text">
        운영 콘솔 계정을 발급·관리합니다. 자가입은 없으며 모든 계정은 여기서 ADMIN이 발급합니다.
        비활성화·비밀번호 초기화 시 해당 사용자의 모든 로그인 세션이 즉시 종료됩니다.
      </p>

      {actionError ? <div className="banner error">{actionError}</div> : null}
      {issuedPassword ? (
        <div className="banner success">
          <strong>{issuedPassword.username}</strong>의 임시 비밀번호:{" "}
          <span className="mono">{issuedPassword.password}</span> — 지금만 표시되니 안전한 채널로
          전달하세요.
        </div>
      ) : null}

      <section className="glass-card">
        <div className="section-head compact">
          <div>
            <p className="section-kicker">계정 발급</p>
            <h2>새 사용자 생성</h2>
          </div>
        </div>
        <form className="workflow-row user-create-form" onSubmit={handleCreate}>
          <label className="workflow-field">
            아이디 <span className="required-mark">*</span>
            <input
              type="text"
              value={newUsername}
              onChange={(event) => setNewUsername(event.target.value)}
              placeholder="영문/숫자/._-"
              minLength={3}
              maxLength={50}
              pattern="[a-zA-Z0-9._\-]+"
              required
            />
          </label>
          <label className="workflow-field">
            표시 이름
            <input
              type="text"
              value={newDisplayName}
              onChange={(event) => setNewDisplayName(event.target.value)}
              placeholder="예: 김분석"
              maxLength={100}
            />
          </label>
          <label className="workflow-field">
            역할
            <select value={newRole} onChange={(event) => setNewRole(event.target.value as Role)}>
              <option value="ANALYST">분석가 (ANALYST)</option>
              <option value="ADMIN">관리자 (ADMIN)</option>
            </select>
          </label>
          <div className="workflow-submit user-create-submit">
            <button type="submit" className="primary-button" disabled={creating}>
              {creating ? "생성 중…" : "계정 생성"}
            </button>
          </div>
        </form>
        <p className="hint-text" style={{ marginTop: 10, marginBottom: 0 }}>
          비밀번호는 서버가 생성해 위 배너에 1회 표시됩니다.
        </p>
      </section>

      <section className="panel-card" style={{ marginTop: 18 }}>
        <DataState
          loading={loading && users.length === 0}
          error={error}
          onRetry={load}
          empty={!loading && !error && users.length === 0}
          emptyMessage="등록된 사용자가 없습니다."
        />

        {users.length > 0 ? (
          <div className="table-scroll">
            <table className="data-table" aria-label="사용자 목록">
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">아이디</th>
                  <th scope="col">표시 이름</th>
                  <th scope="col">역할</th>
                  <th scope="col">상태</th>
                  <th scope="col">최근 로그인</th>
                  <th scope="col">생성일</th>
                  <th scope="col">관리</th>
                </tr>
              </thead>
              <tbody>
                {users.map((row) => {
                  const isSelf = currentUser?.username === row.username;
                  const busy = busyId === row.id;
                  return (
                    <tr key={row.id}>
                      <td className="num">{row.id}</td>
                      <td>
                        <strong>{row.username}</strong>
                        {isSelf ? <span className="cell-muted"> (나)</span> : null}
                      </td>
                      <td>{row.displayName ?? "-"}</td>
                      <td>
                        <select
                          aria-label={`${row.username} 역할 변경`}
                          value={row.role}
                          disabled={busy || isSelf}
                          onChange={(event) => handleRoleChange(row, event.target.value as Role)}
                        >
                          <option value="ANALYST">{ROLE_LABELS.ANALYST}</option>
                          <option value="ADMIN">{ROLE_LABELS.ADMIN}</option>
                        </select>
                      </td>
                      <td>
                        <span className={`status-pill ${row.active ? "component-up" : "component-disabled"}`}>
                          {row.active ? "활성" : "비활성"}
                        </span>
                      </td>
                      <td className="cell-time">
                        {row.lastLoginAt ? formatFullDate(row.lastLoginAt) : "-"}
                      </td>
                      <td className="cell-time">{formatFullDate(row.createdAt)}</td>
                      <td>
                        <div className="badge-group">
                          <button
                            type="button"
                            className="ghost-button"
                            disabled={busy || isSelf}
                            onClick={() => handleToggleActive(row)}
                          >
                            {row.active ? "비활성화" : "활성화"}
                          </button>
                          <button
                            type="button"
                            className="ghost-button"
                            disabled={busy}
                            onClick={() => handlePasswordReset(row)}
                          >
                            비번 초기화
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>
    </>
  );
}
