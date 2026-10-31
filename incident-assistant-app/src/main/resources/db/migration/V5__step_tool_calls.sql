-- Tool call trace of an AI step that may use tools (A-0015): one JSON object per requested call,
-- in order, with tool name, arguments as sent, outcome, duration and result size; no results.
-- Null for steps without tools.
alter table incident_step add column tool_calls jsonb;
