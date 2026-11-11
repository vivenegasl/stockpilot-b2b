export interface MetricPoint {
  time: string;
  confirmed: number;
  insufficientStock: number;
  lockTimeMs: number;
}

export interface PrometheusMetricsSummary {
  ordersCreated: number;
  ordersConfirmed: number;
  ordersCancelled: number;
  ordersFulfilled: number;
  insufficientStockFailures: number;
  idempotencyReplays: number;
  hikariActiveConnections: number;
  hikariTotalConnections: number;
  jvmHeapUsedMb: number;
  jvmHeapMaxMb: number;
}
