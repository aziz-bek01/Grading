import { Fragment } from 'react';

/**
 * Renders comment body with inline mentions.
 *
 * Mention syntax: `@[uuid|Display Name]`
 *  - Backend persists mentions in CommentMention table for audit.
 *  - Frontend visually highlights the mention with a chip + tooltip.
 */
const MENTION_RE = /@\[([0-9a-f-]{6,})\|([^\]]+)\]/gi;

interface Props {
  body: string;
}

export function MentionText({ body }: Props) {
  const parts: Array<{ type: 'text'; value: string } | { type: 'mention'; userId: string; name: string }> = [];
  let last = 0;
  for (const m of body.matchAll(MENTION_RE)) {
    const start = m.index ?? 0;
    if (start > last) parts.push({ type: 'text', value: body.slice(last, start) });
    parts.push({ type: 'mention', userId: m[1], name: m[2] });
    last = start + m[0].length;
  }
  if (last < body.length) parts.push({ type: 'text', value: body.slice(last) });

  return (
    <p className="text-sm text-text-primary whitespace-pre-wrap break-words" data-testid="mention-text">
      {parts.map((p, i) =>
        p.type === 'text' ? (
          <Fragment key={i}>{p.value}</Fragment>
        ) : (
          <span
            key={i}
            data-testid="mention-chip"
            data-user-id={p.userId}
            title={p.userId}
            className="inline-flex items-center bg-primary-50 text-primary-700 border border-primary-500/30 rounded-md px-1.5 py-0.5 text-xs font-medium mx-0.5"
          >
            @{p.name}
          </span>
        ),
      )}
    </p>
  );
}
