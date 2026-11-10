// PreToolUse(Bash) のガード。人が1コマンドずつ見ていない前提で、取り返しのつかない操作だけを機械的に断る。
// exit 0 で許可、exit 2 で拒否（stderr がエージェントに返る）。作業を細かく縛るルールは入れない。
import { execFileSync } from 'node:child_process';

const TRUNK = 'main';
const BRANCH_TYPES = ['feat', 'fix', 'docs', 'refactor', 'test', 'chore'];

function deny(message) {
  process.stderr.write(`[guard] ${message}\n`);
  process.exit(2);
}

// ヒアドキュメントと -m のメッセージはデータであって実行されないので、検査の前に落とす
function strip(command) {
  return command
    .replace(/<<-?\s*'?(\w+)'?[\s\S]*?\n\1\b/g, '')
    .replace(/\s(-m|--message|-F)\s+("[^"]*"|'[^']*'|\S+)/g, ' ');
}

function currentBranch() {
  try {
    return execFileSync('git', ['branch', '--show-current'], { encoding: 'utf8' }).trim();
  } catch {
    return '';
  }
}

function branchNameProblem(name) {
  if (name.includes('claude')) return '`claude` を含めない';
  const [type, slug, ...rest] = name.split('/');
  if (!BRANCH_TYPES.includes(type) || !slug || rest.length > 0)
    return `\`<type>/<slug>\` の形にする（type は ${BRANCH_TYPES.join(' / ')}）`;
  if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(slug)) return 'slug は英小文字・数字・ハイフンだけにする';
  if (/^\d+$/.test(slug.replace(/-/g, ''))) return '番号だけにしない';
  return null;
}

const raw = await new Promise((resolve) => {
  let data = '';
  process.stdin.on('data', (chunk) => (data += chunk));
  process.stdin.on('end', () => resolve(data));
});
let input;
try {
  input = JSON.parse(raw);
} catch {
  process.exit(0); // 入力が読めないときに作業を止める理由はない
}

const segments = strip(input?.tool_input?.command ?? '').split(/;|&&|\|\||\||\n/);
for (const segment of segments) {
  const t = segment.trim().split(/\s+/).filter(Boolean);
  const gi = t.indexOf('git');
  if (gi < 0) continue;
  const [sub, ...args] = t.slice(gi + 1).filter((x, i, a) => !(a[i - 1] === '-C'));
  const plain = args.filter((a) => !a.startsWith('-'));

  if (sub === 'push') {
    if (plain.some((a) => a === TRUNK || a.endsWith(`:${TRUNK}`)))
      deny('main への直接 push は禁止です。作業ブランチを切って PR にしてください。');
    if (plain.length <= 1 && currentBranch() === TRUNK)
      deny('main の上から push しようとしています。作業ブランチを切ってください。');
    if (args.some((a) => a === '--force' || a === '-f'))
      deny('force push は禁止です。必要なら --force-with-lease を使ってください。');
  }
  if (sub === 'add' && args.some((a) => ['-A', '--all', '.', '-u'].includes(a)))
    deny('一括の stage は禁止です。コミットに入れるファイルを明示してください。');
  if (sub === 'reset' && args.includes('--hard'))
    deny('git reset --hard は禁止です。作業が消えます。');
  if (sub === 'clean' && args.some((a) => /^-\w*f/.test(a)))
    deny('git clean -f は禁止です。追跡していないファイルが消えます。');

  const creates =
    (sub === 'switch' && (args.includes('-c') || args.includes('-C'))) ||
    (sub === 'checkout' && (args.includes('-b') || args.includes('-B'))) ||
    (sub === 'branch' && (args.includes('-m') || args.includes('-M') || plain.length === 1));
  if (creates && plain.length > 0) {
    const name = plain[plain.length - 1];
    const problem = branchNameProblem(name);
    if (problem && name !== TRUNK) deny(`ブランチ名 \`${name}\`: ${problem}。`);
  }
}
process.exit(0);
