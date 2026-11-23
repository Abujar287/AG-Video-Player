function srtTimeToVtt(time: string): string {
  return time.trim().replace(',', '.');
}

function assTimeToVtt(time: string): string {
  // ASS time format: H:MM:SS.cc -> 0H:MM:SS.cc0
  const parts = time.trim().split(':');
  if (parts.length !== 3) return '00:00:00.000';
  const h = parts[0].padStart(2, '0');
  const m = parts[1].padStart(2, '0');
  const secParts = parts[2].split('.');
  const s = (secParts[0] || '00').padStart(2, '0');
  const cs = (secParts[1] || '00').padEnd(3, '0').slice(0, 3);
  return `${h}:${m}:${s}.${cs}`;
}

export function convertSubtitleTextToVtt(content: string, fileName: string): string {
  const normalized = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim();
  const lower = fileName.toLowerCase();

  if (lower.endsWith('.vtt') || normalized.startsWith('WEBVTT')) {
    return normalized.startsWith('WEBVTT') ? normalized : `WEBVTT\n\n${normalized}`;
  }

  if (lower.endsWith('.ass') || lower.endsWith('.ssa')) {
    const lines = normalized.split('\n');
    const cues: string[] = ['WEBVTT', ''];
    for (const line of lines) {
      if (!line.startsWith('Dialogue:')) continue;
      const body = line.slice('Dialogue:'.length).trim();
      const cols = body.split(',');
      if (cols.length < 10) continue;
      const start = assTimeToVtt(cols[1]);
      const end = assTimeToVtt(cols[2]);
      const rawText = cols.slice(9).join(',');
      const cleanText = rawText
        .replace(/\{[^}]*\}/g, '')
        .replace(/\\N/g, '\n')
        .replace(/\\n/g, '\n')
        .trim();
      if (cleanText) {
        cues.push(`${start} --> ${end}`, cleanText, '');
      }
    }
    return cues.join('\n');
  }

  // Default: SRT -> WebVTT
  const blocks = normalized.split(/\n{2,}/);
  const vttLines: string[] = ['WEBVTT', ''];
  for (const block of blocks) {
    const lines = block.split('\n').map((l) => l.trimEnd());
    if (lines.length === 0) continue;
    let timeLineIdx = lines.findIndex((l) => l.includes('-->'));
    if (timeLineIdx === -1) continue;
    const [rawStart, rawEnd] = lines[timeLineIdx].split('-->').map((s) => s.trim());
    if (!rawStart || !rawEnd) continue;
    const start = srtTimeToVtt(rawStart);
    const end = srtTimeToVtt(rawEnd.split(/\s+/)[0]);
    const textLines = lines.slice(timeLineIdx + 1).join('\n').trim();
    if (textLines) {
      vttLines.push(`${start} --> ${end}`, textLines, '');
    }
  }
  return vttLines.join('\n');
}

export function createSubtitleBlobUrl(content: string, fileName: string): string {
  const vtt = convertSubtitleTextToVtt(content, fileName);
  const blob = new Blob([vtt], { type: 'text/vtt;charset=utf-8' });
  return URL.createObjectURL(blob);
}
