# Project Rules for ChatNook

## Core Development Principles

### 1. Development Process & Workflow
- Use GitHub repository for the entire project, no local-only code
- All APK builds must happen in GitHub Actions workflow (release-only, never debug builds)
- Build APKs only after a batch of features is implemented, tested, and stable
- Ensure builds never break previous versions when merged
- Use professional software development practices (branches, PR reviews, CI/CD)

### 2. Code Reuse Philosophy
- **Always** use pre-made libraries, modules, open-source projects instead of building from scratch
- Only build from scratch when absolutely necessary
- Use best-in-class solutions for every component

### 3. Technical Dependencies & Architecture
- Android-only for prototype (iOS later via PWD)
- No money spent on any tools or services
- Must work with free-tier hosting initially, with migration path
- Text-chat only initially (no voice/video until later)

### 4. UI & Design Approach
- Use existing UI component libraries (see below), not custom designs
- Recommended libraries:
  - https://reactbits.dev/components
  - https://magicui.design/docs/components  
  - https://ui.aceternity.com/components
  - https://cuicui.day/application-ui
  - https://ui.shadcn.com/docs/components
  - https://ui.indie-starter.dev/docs/loader-dots

### 5. Quality Assurance
- APK will only be released after stability testing
- Basic Nitro features must be polished before release
- No "shipping beta" - everything must be production-ready

### 6. Collaboration
- Works with AI agents (no solo human development)
- Documentation and explanations required for complex decisions
- Professional code standards expected

### 7. Community & User Base
- Initial user base: 20-50 friends
- Group-focused app, not mass-market
- Privacy-conscious design for small trusted group

### 8. Constraints (Hard Rules - No Exceptions)
- No money spent on any tools/services
- No iOS development initially
- APK build only in GitHub Actions
- Features only added when complete and tested
- UI from component libraries, not custom designs

## Development Timeline Expectations

### Phase 1 (Week 1): Foundation
- GitHub repo setup with CI/CD
- Android boilerplate with Jetpack Compose
- Basic user auth (anonymous/email)
- GitHub Actions for release APK builds

### Phase 2 (Week 2-3): Core Chat
- Real-time messaging
- User profiles and basic Nitro features
- Custom profile banner/GIF (simplest first)

### Phase 3 (Week 4): Nitro Features
- Theme colors, animated avatars
- Custom emoji, status, badges
- Profile effects

### Phase 4 (Week 5): Stability & Release
- Thorough testing
- Production-ready APK
- Migration plan from free tiers

## Failure Criteria
- Any feature that requires money
- Building from scratch when a library exists
- Releasing unstable APKs
- Missing any hard rule above

## Success Criteria
- Functional text chat app for 20-50 users
- All Nitro features working without money
- Professional CI/CD pipeline
- Production-ready APK
- Migration path to paid hosting

These rules are **non-negotiable** and must be followed at all times. Deviations require explicit justification and approval.

Updated: 2026-10-23
