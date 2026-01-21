import crypto from "crypto";

/**
 * 
 * 暗号学的に安全なランダム文字列を生成する。
 * 
 * @param byteLength 16バイト（128ビット）以上あると十分なセキュリティ強度が確保できる。
 * @returns BASE64のランダム文字列
 */
export function generateSecureRandomString(byteLength = 32) {
  return crypto.randomBytes(byteLength).toString("base64url");
}

export const generateSalt = () => {
  return crypto.randomBytes(16).toString("hex");
}

export const hashToken = (token: string, salt: string) => {
  return crypto.createHash("sha256")
      .update(process.env.TOKEN_HASH_PEPPER!)
      .update(salt)
      .update(token)
      .digest("hex")
}

export const isValidToken = (tokenHash: string, salt: string, token: string) => {
  const hash = crypto.createHash("sha256")
      .update(process.env.TOKEN_HASH_PEPPER!)
      .update(salt)
      .update(token)
      .digest("hex")
  return crypto.timingSafeEqual(Buffer.from(tokenHash), Buffer.from(hash));
}