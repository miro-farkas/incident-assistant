// Generic, read-only view of a JSON value as nested key/value lists (A-0010, until the typed step
// views). Everything is rendered as text: values may come from alerts or LLM output (P-0019).

const MAX_DEPTH = 8;

export function JsonView({ value }: { value: unknown }) {
  return <JsonValue value={value} depth={0} />;
}

function JsonValue({ value, depth }: { value: unknown; depth: number }) {
  if (value === null || value === undefined) {
    return <span className="json-null">null</span>;
  }
  if (typeof value !== "object") {
    return <span className={`json-${typeof value}`}>{String(value)}</span>;
  }
  if (depth >= MAX_DEPTH) {
    return <span className="json-string">{JSON.stringify(value)}</span>;
  }
  if (Array.isArray(value)) {
    if (value.length === 0) {
      return <span className="json-null">(empty list)</span>;
    }
    return (
      <ol className="json-list">
        {value.map((item, index) => (
          <li key={index}>
            <JsonValue value={item} depth={depth + 1} />
          </li>
        ))}
      </ol>
    );
  }
  const entries = Object.entries(value);
  if (entries.length === 0) {
    return <span className="json-null">(empty)</span>;
  }
  return (
    <dl className="json-object">
      {entries.map(([key, item]) => (
        <div key={key} className="json-entry">
          <dt>{key}</dt>
          <dd>
            <JsonValue value={item} depth={depth + 1} />
          </dd>
        </div>
      ))}
    </dl>
  );
}
