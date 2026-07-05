interface PaginationProps {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <div className="pagination">
      <button
        type="button"
        className="ghost-button"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
      >
        이전
      </button>
      <span className="pagination-status">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        className="ghost-button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        다음
      </button>
    </div>
  );
}
