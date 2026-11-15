import { useState, useEffect } from 'react'

function ThemeToggle() {
  const [dark, setDark] = useState(() => localStorage.getItem('darkMode') === 'true')

  useEffect(() => {
    if (dark) {
      document.body.classList.add('dark-mode')
    } else {
      document.body.classList.remove('dark-mode')
    }
    localStorage.setItem('darkMode', dark)
  }, [dark])

  return (
    <button className="theme-toggle" onClick={() => setDark(!dark)} title={dark ? 'Switch to Light Mode' : 'Switch to Dark Mode'}>
      {dark ? '☀' : '☽'}
    </button>
  )
}

export default ThemeToggle
