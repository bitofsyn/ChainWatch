import type {
  DetectionEventPage,
  FeedEventItem,
  FeedTransactionItem,
  HealthResponse
} from "./types";

async function requestJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function fetchHealth() {
  return requestJson<HealthResponse>("/api/health");
}

export function fetchEvents() {
  return requestJson<DetectionEventPage>("/api/events?size=5");
}

export function fetchRecentEventFeed() {
  return requestJson<FeedEventItem[]>("/api/feed/recent-events?limit=5");
}

export function fetchRecentTransactionFeed() {
  return requestJson<FeedTransactionItem[]>("/api/feed/recent-transactions?limit=5");
}
