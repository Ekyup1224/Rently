import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App as AntApp, ConfigProvider } from 'antd'
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community'
import App from './App'
import './index.css'

// AG Grid 33+ is modular: features must be registered before any grid renders.
// The community bundle covers the infinite row model these screens use.
ModuleRegistry.registerModules([AllCommunityModule])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider theme={{ token: { colorPrimary: '#1668dc', borderRadius: 6 } }}>
      {/* AntApp supplies the context that message/notification need in React 19. */}
      <AntApp>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
)
