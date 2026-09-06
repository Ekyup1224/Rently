'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { useAuth } from './AuthProvider'

/**
 * Phone-first sign-in. There is no separate "register" step: an unknown number
 * gets an account when it verifies its first code.
 */
export function LoginForm() {
  const { adopt } = useAuth()
  const router = useRouter()
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [stage, setStage] = useState<'phone' | 'code'>('phone')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resendIn, setResendIn] = useState(0)

  // Mirrors the server's cooldown so the resend button is disabled exactly while
  // a retry would be rejected.
  useEffect(() => {
    if (resendIn <= 0) {
      return
    }
    const timer = setInterval(() => setResendIn((seconds) => Math.max(0, seconds - 1)), 1000)
    return () => clearInterval(timer)
  }, [resendIn])

  async function sendCode() {
    setBusy(true)
    setError(null)
    try {
      const challenge = await api.requestOtp(phone)
      setStage('code')
      setResendIn(challenge.resendAfterSeconds)
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setBusy(false)
    }
  }

  async function verify() {
    setBusy(true)
    setError(null)
    try {
      adopt(await api.verifyOtp(phone, code))
      router.push('/')
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card" style={{ maxWidth: 440 }}>
      {error && <div className="alert alert--error">{error}</div>}

      {stage === 'phone' ? (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            void sendCode()
          }}
        >
          <label className="field">
            <span className="field__label">Phone number</span>
            <input
              name="phone"
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              placeholder="9911 2233"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              autoFocus
            />
            <span className="muted small">
              Mongolian numbers can be typed as 8 digits. Others need a country code.
            </span>
          </label>
          <button type="submit" className="button button--block" disabled={busy || !phone.trim()}>
            {busy ? 'Sending…' : 'Send code'}
          </button>
        </form>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            void verify()
          }}
        >
          <label className="field">
            <span className="field__label">Code sent to {phone}</span>
            <input
              name="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              placeholder="123456"
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
              autoFocus
            />
          </label>
          <button type="submit" className="button button--block" disabled={busy || code.length < 4}>
            {busy ? 'Checking…' : 'Sign in'}
          </button>
          <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
            <button
              type="button"
              className="button button--link"
              onClick={() => {
                setStage('phone')
                setCode('')
                setError(null)
              }}
            >
              Change number
            </button>
            <button
              type="button"
              className="button button--link"
              disabled={resendIn > 0 || busy}
              onClick={() => void sendCode()}
            >
              {resendIn > 0 ? `Resend in ${resendIn}s` : 'Resend code'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
