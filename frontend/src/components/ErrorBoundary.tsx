import { Component } from "react";
import type { ErrorInfo, ReactNode } from "react";

interface ErrorBoundaryProps {
  /** 라우트 등 경계를 리셋할 기준 키. 바뀌면 오류 상태를 초기화한다. */
  resetKey?: string;
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * 페이지 렌더링 중 예외가 나도 내비게이션은 살아있도록 콘텐츠 영역만 감싸는 오류 경계.
 * 라우트 이동(resetKey 변경) 시 자동으로 복구를 시도한다.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("[ErrorBoundary]", error, info.componentStack);
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps) {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  handleRetry = () => {
    this.setState({ error: null });
  };

  render() {
    if (this.state.error) {
      return (
        <div className="data-state error" role="alert">
          <strong>화면을 표시하는 중 문제가 발생했습니다</strong>
          <p>일시적인 오류일 수 있습니다. 다시 시도하거나 관제 현황으로 이동해주세요.</p>
          <div className="badge-group">
            <button type="button" className="ghost-button" onClick={this.handleRetry}>
              다시 시도
            </button>
            <a className="ghost-button" href="#/">
              관제 현황으로 이동
            </a>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
