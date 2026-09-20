'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { useAuth } from './AuthProvider'
import { useT } from '@/lib/i18n'

/**
 * Phone-first sign-in. There is no separate "register" step: an unknown number
 * gets an account when it verifies its first code.
 */
export function LoginForm() {
  const { adopt } = useAuth()
  const router = useRouter()
  const t = useT()
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [stage, setStage] = useState<'phone' | 'code'>('phone')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resendIn, setResendIn] = useState(0)
  const [devCode, setDevCode] = useState<string | null>(null)

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
      // A development server hands the code straight back. Fill it in, and on a
      // resend replace what is already there, or the stale code gets submitted.
      setDevCode(challenge.devCode ?? null)
      if (challenge.devCode) {
        setCode(challenge.devCode)
      }
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
            <span className="field__label">{t('auth.phone')}</span>
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
            <span className="muted small">{t('auth.help')}</span>
          </label>
          <button type="submit" className="button button--block" disabled={busy || !phone.trim()}>
            {busy ? t('auth.sending') : t('auth.sendCode')}
          </button>
        </form>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            void verify()
          }}
        >
          {devCode && (
            <div className="alert alert--info">
              <strong>{t('auth.devTitle', { code: devCode })}</strong>
              <br />
              {t('auth.devBody')}
            </div>
          )}
          <label className="field">
            <span className="field__label">{t('auth.codeSentTo', { phone })}</span>
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
            {busy ? t('auth.verifying') : t('auth.verify')}
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
              {t('auth.changeNumber')}
            </button>
            <button
              type="button"
              className="button button--link"
              disabled={resendIn > 0 || busy}
              onClick={() => void sendCode()}
            >
              {resendIn > 0 ? t('auth.resendIn', { seconds: resendIn }) : t('auth.resend')}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
