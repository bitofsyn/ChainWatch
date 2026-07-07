interface DataStateProps {
  /** 로딩 중 */
  loading?: boolean;
  /** 오류 메시지 (있으면 오류 상태) */
  error?: string | null;
  /** 권한 없음 (403) 상태 */
  unauthorized?: boolean;
  unauthorizedMessage?: string;
  /** 데이터 없음 상태 */
  empty?: boolean;
  emptyMessage?: string;
  /** 오류/권한 상태에서 재시도 버튼 노출 */
  onRetry?: () => void;
  loadingMessage?: string;
}

/**
 * 목록/상세 화면 공통의 로딩·오류·권한없음·빈 상태 렌더러.
 * 상태에 해당하지 않으면 null을 반환하므로 콘텐츠 위에 조건 없이 배치할 수 있다.
 */
export function DataState({
  loading,
  error,
  unauthorized,
  unauthorizedMessage = "이 화면을 볼 수 있는 권한이 없습니다. 관리자 계정으로 로그인했는지 확인해주세요.",
  empty,
  emptyMessage = "표시할 데이터가 없습니다.",
  onRetry,
  loadingMessage = "불러오는 중..."
}: DataStateProps) {
  if (loading) {
    return (
      <div className="data-state loading" role="status">
        {loadingMessage}
      </div>
    );
  }
  if (unauthorized) {
    return (
      <div className="data-state unauthorized" role="alert">
        <strong>접근 권한 없음</strong>
        <p>{unauthorizedMessage}</p>
      </div>
    );
  }
  if (error) {
    return (
      <div className="data-state error" role="alert">
        <p>{error}</p>
        {onRetry ? (
          <button type="button" className="ghost-button" onClick={onRetry}>
            다시 시도
          </button>
        ) : null}
      </div>
    );
  }
  if (empty) {
    return <div className="data-state empty">{emptyMessage}</div>;
  }
  return null;
}
