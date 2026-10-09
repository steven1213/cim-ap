// 口令第一层派生（浏览器侧，明文口令不出浏览器）。
//
// 算法与 server 端 PasswordDerivation 严格对齐：
//   clientHash = PBKDF2WithHmacSHA256(password, utf8(clientSalt), rounds)  → 256bit → hex
// 注意：salt 取其「字符串的 UTF-8 字节」（与 server 端 salt.getBytes(UTF_8) 一致），
//       不是把十六进制盐串解码成字节。rounds 必须与 IamAuthProperties.Password.rounds 一致。

export const PBKDF2_DEFAULT_ROUNDS = 100_000;

function utf8Bytes(str: string): Uint8Array<ArrayBuffer> {
  const bytes = new TextEncoder().encode(str);
  // 复制为 ArrayBuffer 支撑的数组，规避 TS 5.7 下 Uint8Array<ArrayBufferLike> 与 BufferSource 的不兼容
  return new Uint8Array(bytes);
}

function toHex(bytes: Uint8Array): string {
  let out = '';
  for (const b of bytes) {
    out += b.toString(16).padStart(2, '0');
  }
  return out;
}

/** 生成随机 clientSalt（16 字节 → 32 位十六进制串），用于注册/未知用户兜底。 */
export function randomClientSalt(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return toHex(bytes);
}

/** 第一层派生：明文口令 → clientHash（十六进制）。 */
export async function deriveClientHash(
  password: string,
  salt: string,
  rounds: number = PBKDF2_DEFAULT_ROUNDS,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    'raw',
    utf8Bytes(password),
    { name: 'PBKDF2' },
    false,
    ['deriveBits'],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: utf8Bytes(salt), iterations: rounds, hash: 'SHA-256' },
    key,
    256,
  );
  return toHex(new Uint8Array(bits));
}
