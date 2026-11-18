import { useEffect, useRef, useState } from 'react'
import * as pdfjs from 'pdfjs-dist'
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorker

export default function ProtectedPdfViewer({ blob, title }) {
  const containerRef = useRef(null)
  const [state, setState] = useState({ status: 'loading', pages: 0, error: '' })

  useEffect(() => {
    let cancelled = false
    let doc = null
    const container = containerRef.current
    setState({ status: 'loading', pages: 0, error: '' })

    const render = async () => {
      try {
        const data = new Uint8Array(await blob.arrayBuffer())
        doc = await pdfjs.getDocument({ data, disableAutoFetch: true, isEvalSupported: false }).promise
        if (cancelled) return
        const width = Math.min(container.clientWidth || 820, 900)
        for (let n = 1; n <= doc.numPages; n += 1) {
          const page = await doc.getPage(n)
          if (cancelled) return
          const base = page.getViewport({ scale: 1 })
          const viewport = page.getViewport({ scale: width / base.width })
          const canvas = document.createElement('canvas')
          canvas.width = viewport.width
          canvas.height = viewport.height
          canvas.style.display = 'block'
          canvas.style.margin = '0 auto 12px'
          canvas.style.maxWidth = '100%'
          canvas.style.background = '#fff'
          canvas.style.boxShadow = '0 1px 3px rgba(0,0,0,0.15)'
          container.appendChild(canvas)
          await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise
        }
        if (!cancelled) setState({ status: 'ready', pages: doc.numPages, error: '' })
      } catch (err) {
        if (!cancelled) setState({ status: 'error', pages: 0, error: 'The statement could not be displayed.' })
      }
    }
    render()

    return () => {
      cancelled = true
      if (doc) doc.destroy()
      if (container) container.replaceChildren()
    }
  }, [blob])

  const block = (event) => event.preventDefault()

  return (
    <div
      role="document"
      aria-label={title}
      onContextMenu={block}
      onDragStart={block}
      onCopy={block}
      style={{
        border: '1px solid #dfe3e8',
        borderRadius: 8,
        background: '#eef1f5',
        padding: 12,
        height: 720,
        overflowY: 'auto',
        userSelect: 'none',
      }}
    >
      {state.status === 'loading' && (
        <div className="sf-skeleton sf-skeleton-page" aria-label="Loading statement" role="status" />
      )}
      {state.status === 'error' && <p className="cu-banner danger" role="alert">{state.error}</p>}
      <div ref={containerRef} />
    </div>
  )
}
