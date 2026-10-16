// Skia moved many GPU headers between milestones (include/gpu/X.h -> include/gpu/ganesh/X.h).
// This header picks whichever layout the Skia checkout actually has, so the build doesn't depend on the milestone.
#pragma once

#if __has_include("include/gpu/ganesh/GrDirectContext.h")
#include "include/gpu/ganesh/GrDirectContext.h"
#else
#include "include/gpu/GrDirectContext.h"
#endif

#if __has_include("include/gpu/ganesh/GrBackendSurface.h")
#include "include/gpu/ganesh/GrBackendSurface.h"
#else
#include "include/gpu/GrBackendSurface.h"
#endif

#if __has_include("include/gpu/ganesh/GrTypes.h")
#include "include/gpu/ganesh/GrTypes.h"
#elif __has_include("include/gpu/GrTypes.h")
#include "include/gpu/GrTypes.h"
#endif

#if __has_include("include/gpu/ganesh/gl/GrGLInterface.h")
#include "include/gpu/ganesh/gl/GrGLInterface.h"
#else
#include "include/gpu/gl/GrGLInterface.h"
#endif

#if __has_include("include/gpu/ganesh/gl/GrGLTypes.h")
#include "include/gpu/ganesh/gl/GrGLTypes.h"
#else
#include "include/gpu/gl/GrGLTypes.h"
#endif

#include "include/gpu/ganesh/SkSurfaceGanesh.h"

// EGL interface
#if __has_include("include/gpu/ganesh/gl/egl/GrGLMakeEGLInterface.h")
#include "include/gpu/ganesh/gl/egl/GrGLMakeEGLInterface.h"
#define FIGTAB_MAKE_EGL_INTERFACE() GrGLInterfaces::MakeEGL()
#else
#include "include/gpu/gl/egl/GrGLMakeEGLInterface.h"
#define FIGTAB_MAKE_EGL_INTERFACE() GrGLMakeEGLInterface()
#endif

// GL direct context
#if __has_include("include/gpu/ganesh/gl/GrGLDirectContext.h")
#include "include/gpu/ganesh/gl/GrGLDirectContext.h"
#define FIGTAB_MAKE_GL_CONTEXT(iface) GrDirectContexts::MakeGL(iface)
#else
#define FIGTAB_MAKE_GL_CONTEXT(iface) GrDirectContext::MakeGL(iface)
#endif

// Backend render target wrapping the default framebuffer
#if __has_include("include/gpu/ganesh/gl/GrGLBackendSurface.h")
#include "include/gpu/ganesh/gl/GrGLBackendSurface.h"
#define FIGTAB_MAKE_GL_RT(w, h, fb) GrBackendRenderTargets::MakeGL((w), (h), 0, 8, (fb))
#else
#define FIGTAB_MAKE_GL_RT(w, h, fb) GrBackendRenderTarget((w), (h), 0, 8, (fb))
#endif
