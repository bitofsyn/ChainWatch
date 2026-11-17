import { useEffect, useState } from "react";
import { fetchPipelineStatus, fetchStakingObservations } from "../api";
import { useAuth } from "../contexts/AuthContext";

/**
 * 배포 구성에 따라 꺼져 있을 수 있는 선택 기능의 사용 가능 여부.
 * null은 "아직 모름"이다. 확인 전에는 메뉴·버튼을 숨기지 않고 기존 동작을 유지한다.
 */
export interface FeatureFlags {
  /** AI 분석 서버 연동 여부. false면 분석 요청 버튼과 재분석 메뉴를 숨긴다. */
  aiEnabled: boolean | null;
  /** 검증인(스테이킹) 조회 기능 설정 여부. 관리자만 확인한다. */
  stakingEnabled: boolean | null;
}

const UNKNOWN: FeatureFlags = { aiEnabled: null, stakingEnabled: null };

// 화면마다 다시 묻지 않도록 로그인한 사용자 단위로 한 번만 조회한다.
let cache: { key: string; promise: Promise<FeatureFlags> } | null = null;

async function load(isAdmin: boolean): Promise<FeatureFlags> {
  const [pipeline, staking] = await Promise.all([
    fetchPipelineStatus().catch(() => null),
    isAdmin ? fetchStakingObservations().catch(() => null) : Promise.resolve(null)
  ]);
  const ai = pipeline?.components.find((component) => component.name === "aiServer");
  return {
    aiEnabled: ai ? ai.status !== "DISABLED" : null,
    stakingEnabled: staking ? staking.enabled : null
  };
}

export function useFeatureFlags(): FeatureFlags {
  const { user, isAdmin } = useAuth();
  const [flags, setFlags] = useState<FeatureFlags>(UNKNOWN);
  const key = user ? `${user.username}:${user.role}` : null;

  useEffect(() => {
    if (!key) {
      setFlags(UNKNOWN);
      return;
    }
    if (cache?.key !== key) {
      cache = { key, promise: load(isAdmin) };
    }
    let active = true;
    cache.promise.then((next) => {
      if (active) {
        setFlags(next);
      }
    });
    return () => {
      active = false;
    };
  }, [key, isAdmin]);

  return flags;
}
