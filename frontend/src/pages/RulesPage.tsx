import { startTransition, useEffect, useState } from "react";
import { fetchDetectionRules } from "../api";
import type { DetectionRules } from "../types";
import { RISK_LEVEL_LABELS, toStatus } from "../lib/format";

export function RulesPage() {
  const [rules, setRules] = useState<DetectionRules | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    fetchDetectionRules()
      .then((data) => {
        if (active) {
          startTransition(() => {
            setRules(data);
            setError(null);
            setLoading(false);
          });
        }
      })
      .catch(() => {
        if (active) {
          startTransition(() => {
            setError("탐지 규칙 조회에 실패했습니다. 백엔드 상태를 확인해주세요.");
            setLoading(false);
          });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <>
      <section className="page-head">
        <div>
          <p className="eyebrow">탐지 기준</p>
          <h1>탐지 규칙과 위험 스코어 산정 기준</h1>
          <p className="page-lede">
            ChainWatch는 Rule Engine이 트랜잭션 단위로 아래 규칙을 평가해 이상거래를
            탐지합니다. AI 리포트는 탐지 결과를 설명하는 보조 자료이며, 탐지 자체는 규칙
            기반으로 결정됩니다.
          </p>
        </div>
        {error ? <div className="banner error">{error}</div> : null}
      </section>

      {loading ? <div className="empty-state">불러오는 중...</div> : null}

      {rules ? (
        <>
          <section className="glass-card rules-mode">
            <p>
              탐지 트리거 모드: <strong>{rules.mode}</strong>
              {rules.mode === "KAFKA"
                ? " — Kafka raw-transactions 토픽을 구독하는 Consumer가 비동기로 탐지합니다."
                : " — 수집 트랜잭션과 같은 트랜잭션 경계 안에서 동기 탐지합니다."}
            </p>
          </section>

          <section className="rules-grid">
            {rules.rules.map((rule) => (
              <article className="glass-card rule-card" key={rule.eventType}>
                <div className="section-head compact">
                  <div>
                    <p className="section-kicker">{rule.eventType}</p>
                    <h2>{rule.name}</h2>
                  </div>
                  <span className={`status-pill ${rule.active ? "elevated" : "lifecycle-resolved"}`}>
                    {rule.active ? "활성" : "조건 미설정"}
                  </span>
                </div>
                <p className="rule-description">{rule.description}</p>
                <dl className="detail-list">
                  <div>
                    <dt>임계값</dt>
                    <dd>{rule.threshold}</dd>
                  </div>
                  <div>
                    <dt>기본 등급</dt>
                    <dd>
                      <span className={`status-pill ${toStatus(rule.baseRiskScore)}`}>
                        {RISK_LEVEL_LABELS[rule.baseRiskLevel] ?? rule.baseRiskLevel}
                      </span>{" "}
                      위험 점수 {rule.baseRiskScore}
                    </dd>
                  </div>
                </dl>
              </article>
            ))}
          </section>
        </>
      ) : null}
    </>
  );
}
