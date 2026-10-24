import styles from "@/app/dashboard.module.css";

type Status = "ok" | "down" | "unknown";

const DOT: Record<Status, string> = {
  ok: styles.dotOk,
  down: styles.dotBad,
  unknown: styles.dotWarn,
};

const LABEL: Record<Status, string> = {
  ok: "API healthy",
  down: "API unreachable",
  unknown: "Checking…",
};

export function HealthBadge({ status }: { status: Status }) {
  return (
    <span className={styles.statusRow}>
      <span className={`${styles.dot} ${DOT[status]}`} />
      {LABEL[status]}
    </span>
  );
}
