import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { ArrowUpRight, BarChart3, Check, ChevronDown, Download, Eye, EyeOff, FileText, LockKeyhole, Mail, Search, ShieldCheck, Sparkles, Upload, X } from 'lucide-react'
import './styles.css'

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000'
const authHeaders = () => {
  const token = localStorage.getItem('talentlens_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}
const sampleJob = `We are looking for a Product-minded Full Stack Engineer to build thoughtful recruiting tools. You will work with React, TypeScript, Python, FastAPI, PostgreSQL, Docker, and AWS. Strong communication and experience designing REST APIs are important. Experience with machine learning or NLP is a plus.`
const sampleCandidates = [
  { name: 'Ava Rodriguez', text: 'Full Stack Engineer with 6 years experience building React and TypeScript products. Strong Python, FastAPI, PostgreSQL, REST API and Docker background. Deployed services on AWS and led agile teams.' },
  { name: 'Marcus Chen', text: 'Frontend developer with 4 years experience in React, JavaScript, HTML, CSS and Figma. Collaborates with product and design teams. Familiar with Node.js, Git and REST API integration.' },
  { name: 'Priya Shah', text: 'Machine learning engineer with 5 years experience using Python, NLP, scikit-learn, pandas, TensorFlow and AWS. Built production APIs with FastAPI and Docker, with PostgreSQL data pipelines.' },
]

function SignIn({ onSignIn }) {
  const [mode, setMode] = useState('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    if (!email.includes('@') || password.length < 6) {
      setError('Enter a valid work email and a password with 6+ characters.')
      return
    }
    setError(''); setLoading(true)
    try {
      const response = await fetch(`${API_URL}/api/auth/${mode}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload.detail || 'Authentication failed')
      localStorage.setItem('talentlens_token', payload.token)
      onSignIn(payload.user)
    } catch (err) { setError(err.message || 'Could not reach the authentication service.') }
    finally { setLoading(false) }
  }

  return <main className="auth-shell">
    <section className="auth-visual">
      <div className="auth-brand"><span className="brand-mark"><Sparkles size={16} /></span><span>talent<span>lens</span></span></div>
      <div className="auth-message"><p className="eyebrow">RECRUITING INTELLIGENCE / 01</p><h1>Make every<br /><em>hire more human.</em></h1><p>One calm workspace for the signal behind every resume.</p></div>
      <div className="auth-footnote"><span className="status-dot" /> Private workspace / encrypted by design</div>
    </section>
    <section className="auth-form-side">
      <div className="auth-form-wrap"><div className="mobile-auth-brand"><span className="brand-mark"><Sparkles size={16} /></span><span>talent<span>lens</span></span></div><p className="eyebrow">{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</p><h2>{mode === 'login' ? 'Sign in to your workspace' : 'Create your workspace'}</h2><p className="auth-subtitle">{mode === 'login' ? 'Pick up where your hiring team left off.' : 'Start building clearer shortlists today.'}</p>
        <form onSubmit={submit} className="auth-form"><label>Work email<div className="auth-input"><Mail size={17} /><input type="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="you@company.com" autoComplete="email" /></div></label><label>Password<div className="auth-input"><LockKeyhole size={17} /><input type={showPassword ? 'text' : 'password'} value={password} onChange={event => setPassword(event.target.value)} placeholder="Enter your password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /><button type="button" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div></label><div className="auth-options"><label className="remember"><input type="checkbox" /> <span>Remember me</span></label>{mode === 'login' && <span className="auth-hint">Secure local session</span>}</div>{error && <p className="auth-error">{error}</p>}<button className="sign-in-button" type="submit" disabled={loading}>{loading ? 'Connecting...' : mode === 'login' ? 'Sign in' : 'Create account'} {!loading && <ArrowUpRight size={17} />}</button></form>
        <p className="auth-legal"><ShieldCheck size={14} /> Passwords are protected with secure hashing.</p><p className="auth-signup">{mode === 'login' ? 'New to TalentLens?' : 'Already have an account?'} <button type="button" className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>{mode === 'login' ? 'Create an account' : 'Sign in instead'}</button></p>
      </div>
    </section>
  </main>
}

function App() {
  const [signedIn, setSignedIn] = useState(() => Boolean(localStorage.getItem('talentlens_token')))
  const [job, setJob] = useState('')
  const [candidates, setCandidates] = useState([{ name: 'Candidate 01', text: '' }])
  const [results, setResults] = useState(null)
  const [selected, setSelected] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const loadDemo = () => { setJob(sampleJob); setCandidates(sampleCandidates); setResults(null); setError('') }
  const updateCandidate = (index, key, value) => setCandidates(items => items.map((item, i) => i === index ? { ...item, [key]: value } : item))
  const addCandidate = () => setCandidates(items => [...items, { name: `Candidate ${String(items.length + 1).padStart(2, '0')}`, text: '' }])
  const removeCandidate = (index) => setCandidates(items => items.filter((_, i) => i !== index))
  const handleFile = async (file, index = null) => {
    if (!file) return
    const form = new FormData()
    form.append('file', file)
    const response = await fetch(`${API_URL}/api/extract`, { method: 'POST', headers: authHeaders(), body: form })
    if (!response.ok) return setError('That file could not be read. Use PDF, DOCX, or plain text.')
    const payload = await response.json()
    if (index === null) setJob(payload.text)
    else setCandidates(items => items.map((item, i) => i === index ? { ...item, text: payload.text } : item))
  }

  const analyze = async () => {
    setError(''); setLoading(true)
    try {
      const response = await fetch(`${API_URL}/api/analyze`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify({ job_description: job, candidates }) })
      if (!response.ok) throw new Error('Add a job description and at least one resume with 20+ characters.')
      setResults(await response.json()); setSelected(0)
    } catch (err) { setError(err.message || 'Could not reach the matching engine.') }
    finally { setLoading(false) }
  }

  const exportResults = async () => {
    const response = await fetch(`${API_URL}/api/export`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify({ job_description: job, candidates }) })
    const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = 'talentlens-results.csv'; link.click(); URL.revokeObjectURL(url)
  }

  const signOut = async () => {
    await fetch(`${API_URL}/api/auth/logout`, { method: 'POST', headers: authHeaders() })
    localStorage.removeItem('talentlens_token')
    setSignedIn(false)
  }

  const current = results?.results[selected]
  if (!signedIn) return <SignIn onSignIn={() => setSignedIn(true)} />

  return <div className="app-shell">
    <header className="topbar"><div className="brand"><span className="brand-mark"><Sparkles size={16} /></span><span>talent<span>lens</span></span></div><div className="topbar-meta"><span className="status-dot" /> Local matching engine <button className="profile" onClick={signOut} title="Sign out">AM</button></div></header>
    <main>
      <section className="intro"><div><p className="eyebrow">RECRUITING INTELLIGENCE / 01</p><h1>Find the signal<br /><em>inside every resume.</em></h1><p className="lede">Turn a stack of resumes into a clear, explainable shortlist. TalentLens reads for skills, context, and potential, not just keyword density.</p></div><button className="demo-button" onClick={loadDemo}>Load sample workspace <ArrowUpRight size={16} /></button></section>
      <section className="workspace-grid">
        <div className="input-column">
          <div className="section-heading"><span className="step">01</span><div><h2>Role brief</h2><p>What are you hiring for?</p></div></div>
          <div className="panel role-panel"><textarea value={job} onChange={e => setJob(e.target.value)} placeholder="Paste a job description here..." /><div className="panel-footer"><span>{job.length} characters</span><label className="icon-button" title="Upload job description"><Upload size={16} /><input type="file" accept=".pdf,.docx,.txt,.md" hidden onChange={e => handleFile(e.target.files?.[0])} /></label></div></div>
          <div className="section-heading candidate-heading"><span className="step">02</span><div><h2>Candidate stack</h2><p>Add resumes to compare</p></div><span className="count-badge">{candidates.length}</span></div>
          <div className="candidate-list">{candidates.map((candidate, index) => <div className="candidate-input" key={index}><div className="candidate-input-top"><span className="drag-handle">••</span><input value={candidate.name} onChange={e => updateCandidate(index, 'name', e.target.value)} /><div className="candidate-actions"><label title="Upload resume"><Upload size={15} /><input type="file" accept=".pdf,.docx,.txt" hidden onChange={e => handleFile(e.target.files?.[0], index)} /></label>{candidates.length > 1 && <button onClick={() => removeCandidate(index)} title="Remove candidate"><X size={15} /></button>}</div></div><textarea value={candidate.text} onChange={e => updateCandidate(index, 'text', e.target.value)} placeholder="Paste resume text or upload a file..." /><div className="input-hint"><FileText size={13} /> PDF, DOCX, or plain text</div></div>)}</div>
          <button className="add-button" onClick={addCandidate}>+ Add another candidate</button>
          {error && <p className="error-message">{error}</p>}
          <button className="analyze-button" onClick={analyze} disabled={loading}>{loading ? 'Reading the stack...' : <><BarChart3 size={18} /> Analyze candidate fit <ArrowUpRight size={17} /></>}</button>
        </div>
        <div className="results-column"><div className="results-header"><div><p className="eyebrow">MATCH REPORT / LIVE</p><h2>{results ? 'Your shortlist, clarified.' : 'Your analysis awaits.'}</h2></div>{results && <button className="export-button" onClick={exportResults}><Download size={15} /> Export CSV</button>}</div>
          {!results ? <div className="empty-state"><div className="empty-icon"><Search size={22} /></div><h3>Compare candidates side by side</h3><p>Paste your role brief and resumes to see match scores, skill coverage, and the gaps worth discussing.</p><div className="empty-rule" /><span>Results stay in your local workspace</span></div> : (
            <>
              <div className="summary-strip"><div><strong>{results.summary.candidates}</strong><span>candidates read</span></div><div><strong>{results.summary.average_score}%</strong><span>average match</span></div><div><strong>{results.summary.top_score}%</strong><span>top match</span></div></div>
              <div className="result-list">{results.results.map((result, index) => <button className={`result-row ${selected === index ? 'selected' : ''}`} onClick={() => setSelected(index)} key={result.id}><span className="rank">0{index + 1}</span><span className="result-name"><strong>{result.candidate}</strong><small>{result.experience_signal}</small></span><span className="result-meter"><span style={{ width: `${result.score}%` }} /></span><span className="result-score">{result.score}%</span><ChevronDown size={16} className="row-chevron" /></button>)}</div>
              {current && <div className="detail-panel"><div className="detail-top"><div><span className="detail-label">Candidate breakdown</span><h3>{current.candidate}</h3></div><div className="big-score">{current.score}<small>% match</small></div></div><p className="preview">{current.preview}</p><div className="detail-grid"><div><span className="detail-label">Matched skills <b>{current.matched_skills.length}</b></span><div className="chips">{current.matched_skills.map(skill => <span className="chip good" key={skill}><Check size={12} />{skill}</span>)}</div></div><div><span className="detail-label">Skill gaps <b>{current.missing_skills.length}</b></span><div className="chips">{current.missing_skills.length ? current.missing_skills.map(skill => <span className="chip gap" key={skill}><X size={12} />{skill}</span>) : <span className="all-covered">All core skills covered</span>}</div></div></div></div>}
            </>
          )}
        </div>
      </section>
    </main><footer><span>talentlens / resume matcher</span><span>Semantic matching for humans <span className="footer-dot">•</span> v0.1</span></footer>
  </div>
}

createRoot(document.getElementById('root')).render(<App />)
