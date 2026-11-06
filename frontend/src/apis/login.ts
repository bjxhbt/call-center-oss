import Request from "@/utils/axios";

export function handleLogin (data: any) {
  return Request.post('/auth/v1/login', data)
}

export function handleLoginout (data: any) {
  return Request.get('/auth/v1/logout', data)
}

export function changePassword(data: {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}) {
  return Request.post('/system/v1/user/changePassword', data)
}

export function getImgLocation (data: any) {
  return Request.get('/captcha/v1/getCutImage', { responseType: 'arraybuffer' })
}

export function getVerifyImage (data: any) {
  return Request.get('/captcha/v1/verifyImage', { responseType: 'arraybuffer' })
}

export function getSrcImage (data: any) {
  return Request.get('/captcha/v1/getSrcImage', { responseType: 'arraybuffer' })
}
