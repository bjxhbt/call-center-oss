export function generateRandomString(length: number) {
  let result = ''
  const characters = '0123456789'
  const charactersLength = characters.length
  // 确保生成的随机数字串长度为6位
  for (let i = 0; i < length; i++) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength))
  }
  return result
}
