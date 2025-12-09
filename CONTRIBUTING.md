# Contributing to Telegram Android

This guide explains how to submit the Telegram Data Exporter feature to the official Telegram Android repository.

## 📋 Pre-Submission Checklist

Before submitting, ensure:

- [x] All code follows Telegram's coding style
- [x] All files include proper license headers (GNU GPL v. 2)
- [x] Code is tested and working
- [x] No hardcoded API keys or sensitive data
- [x] Documentation is complete
- [x] Screenshots/GIFs are included
- [x] Export functionality respects user privacy
- [x] Error handling is comprehensive

## 🎯 Submission Strategy

### Option 1: Start with a Discussion (Recommended)

**Why start with a discussion?**
- Get feedback from maintainers before writing code
- Align with Telegram's design philosophy
- Understand their review process
- Avoid wasted effort if they have different plans

### Option 2: Direct Pull Request

**When to use:**
- You're confident the feature aligns with their roadmap
- You've reviewed their existing codebase thoroughly
- You're ready for immediate code review

## 📝 Step-by-Step Process

### Step 1: Fork and Clone the Repository

```bash
# Fork the Telegram Android repository on GitHub
# Then clone your fork
git clone https://github.com/YOUR_USERNAME/Telegram.git
cd Telegram
git remote add upstream https://github.com/DrKLO/Telegram.git
```

### Step 2: Create a Feature Branch

```bash
git checkout -b feature/telegram-data-exporter
```

### Step 3: Integrate Your Code

1. **Copy the takeout package** to the appropriate location:
   ```bash
   # From your telegram-data-exporter-android repo
   cp -r takeout/ Telegram/app/src/main/java/org/telegram/ui/
   ```

2. **Update package declarations** to match Telegram's structure:
   ```java
   // Change from: package plus.takeout;
   // To: package org.telegram.ui.takeout;
   ```

3. **Integrate into Settings**:
   - Find `SettingsActivity.java` or similar
   - Add menu item for "Export Data"
   - Wire up `TakeoutFragment`

4. **Handle dependencies**:
   - Ensure all imports resolve
   - Check for any missing dependencies
   - Verify compatibility with Telegram's build system

### Step 4: Test Thoroughly

- [ ] Test on Android 10, 11, 12, 13, 14
- [ ] Test with different account sizes (small, medium, large)
- [ ] Test all export types individually
- [ ] Test error scenarios (network failures, storage full, etc.)
- [ ] Verify exported data integrity
- [ ] Test with RTL languages
- [ ] Test on tablets and phones

### Step 5: Create a Discussion (Recommended First Step)

Go to: https://github.com/DrKLO/Telegram/discussions

**Discussion Title:**
```
[Feature Request] Telegram Data Export Feature
```

**Discussion Body Template:**

```markdown
## Feature: Telegram Data Export

### Overview
I've implemented a complete Telegram data export feature that allows users to export their chats, contacts, media, stories, and other data in CSV/JSON formats.

### Motivation
- Users need to backup their Telegram data
- GDPR compliance (right to data portability)
- Migration between devices/clients
- Data analysis and archival

### Implementation
I've created a complete implementation with:
- ✅ Full UI with progress indicators
- ✅ Support for all data types (chats, contacts, media, stories, etc.)
- ✅ CSV and JSON export formats
- ✅ Proper error handling
- ✅ Uses official Takeout API

### Screenshots
[Include screenshots from your Boltgram app]

### Technical Details
- Uses `account.initTakeoutSession` API
- Implements `invokeWithTakeout` wrapper
- Message pagination via `messages.getSplitRanges`
- Organized file structure in Downloads folder

### Questions
1. Is this feature aligned with Telegram's roadmap?
2. Are there any design/UX preferences I should follow?
3. Should I proceed with a PR, or wait for feedback?
4. Any specific testing requirements?

### Repository
Full implementation available at: [your repo URL]

Looking forward to your feedback!
```

### Step 6: Create a Pull Request

**PR Title:**
```
Add Telegram Data Export Feature
```

**PR Description Template:**

```markdown
## Description
This PR adds a complete Telegram data export feature using the Takeout API, allowing users to export their chats, contacts, media, stories, and other data.

## Changes
- Added `TakeoutFragment` with full UI
- Implemented export cells for all data types:
  - Private messages
  - Group messages
  - Channel messages
  - Saved messages
  - Contacts
  - Personal info
  - Profile pictures
  - Stories
  - Files
  - Sessions
- Added CSV and JSON export formats
- Integrated with Telegram's Takeout API
- Added progress indicators and error handling

## Screenshots
[Include screenshots]

## Testing
- [x] Tested on Android 10-14
- [x] Tested all export types
- [x] Tested error scenarios
- [x] Verified data integrity
- [x] Tested with large accounts

## Related Issues/Discussions
Closes #[issue-number] (if applicable)
Related to #[discussion-number]

## Checklist
- [x] Code follows project style guidelines
- [x] Self-review completed
- [x] Comments added for complex code
- [x] Documentation updated
- [x] No new warnings generated
- [x] Tests added (if applicable)
- [x] All tests pass
```

## 🔍 Key Considerations

### 1. Code Style
- Follow Telegram's existing code style
- Match indentation (likely 4 spaces)
- Follow naming conventions
- Add JavaDoc comments for public methods

### 2. Architecture Alignment
- Review how Telegram structures features
- Follow their fragment/activity patterns
- Use their theming system
- Integrate with their navigation

### 3. Privacy & Security
- Ensure no sensitive data in logs
- Respect user privacy settings
- Handle permissions correctly
- Secure file storage

### 4. Performance
- Optimize for large datasets
- Use background threads appropriately
- Implement proper pagination
- Avoid memory leaks

### 5. Localization
- All strings should be in `strings.xml`
- Support RTL languages
- Use `LocaleController` for formatting

## 📚 Resources

### Telegram Android Repository
- **Main Repo**: https://github.com/DrKLO/Telegram
- **Issues**: https://github.com/DrKLO/Telegram/issues
- **Discussions**: https://github.com/DrKLO/Telegram/discussions
- **Pull Requests**: https://github.com/DrKLO/Telegram/pulls

### Documentation
- Telegram API: https://core.telegram.org/api
- MTProto: https://core.telegram.org/mtproto
- Takeout API: https://core.telegram.org/api/takeout

### Code Review Tips
1. **Be responsive**: Respond to review comments quickly
2. **Be open to feedback**: Maintainers know the codebase best
3. **Keep PRs focused**: Don't mix unrelated changes
4. **Write clear commit messages**: Follow conventional commits if they use them
5. **Be patient**: Large projects have slower review cycles

## 🚨 Common Pitfalls to Avoid

1. **Don't modify core Telegram classes** unless necessary
2. **Don't hardcode strings** - use string resources
3. **Don't ignore Android version differences** (especially storage permissions)
4. **Don't skip error handling** - users will encounter edge cases
5. **Don't forget to test on real devices** - emulators can hide issues

## 📞 Alternative: Contact Telegram Directly

If the GitHub approach doesn't work, consider:

1. **Telegram Support**: https://telegram.org/support
2. **Telegram Developers**: https://core.telegram.org/
3. **Community Forums**: Telegram developer communities

## 🎯 Success Criteria

Your contribution will be successful if:
- ✅ Code is merged into main branch
- ✅ Feature is included in official releases
- ✅ Users can export their data easily
- ✅ Implementation is maintainable

## 📝 License Compliance

Ensure your contribution:
- ✅ Is compatible with GNU GPL v. 2
- ✅ Includes proper copyright headers
- ✅ Doesn't violate any third-party licenses
- ✅ Can be freely distributed

## 🔄 After Submission

1. **Monitor the PR/Discussion**: Respond to comments promptly
2. **Make requested changes**: Be flexible with feedback
3. **Keep your fork updated**: Rebase on upstream changes
4. **Stay engaged**: Help with testing and documentation

## 💡 Tips for Success

- **Start small**: Maybe suggest exporting just messages first
- **Show value**: Demonstrate clear user benefit
- **Be patient**: Large projects move slowly
- **Stay positive**: Maintain a constructive tone
- **Learn from others**: Review existing PRs to understand their process

---

**Good luck with your contribution!** 🚀

Remember: Even if your PR isn't merged immediately, you've created valuable open-source code that others can use and learn from.

