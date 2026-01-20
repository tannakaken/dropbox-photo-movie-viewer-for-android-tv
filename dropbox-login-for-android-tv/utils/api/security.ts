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