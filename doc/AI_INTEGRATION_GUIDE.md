# Ignis AI Integration Guide

## Overview

The Ignis Editor includes an integrated AI assistant powered by **Google Gemini 2.0 Flash API**. This feature allows you to:

- **ASK Mode**: Ask questions about your project and get AI-powered suggestions
- **AGENT Mode**: Let AI automatically create and modify project files based on your tasks

## Setup

### 1. Get Your API Key

1. Visit [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Click "Create API Key"
3. Copy your API key (keep it safe and never commit it to version control)

### 2. Configure API Key in Ignis Editor

1. Open your project in Ignis Editor
2. Go to the **Auxiliary** tab (right panel)
3. Select the **⚙️ Settings** subtab
4. Paste your API key in the password field
5. Click "💾 Save API Key"
6. The status should change to "Status: ✓ Configured"

---

## Using ASK Mode

The ASK mode allows you to query the AI about your project with full context.

### How It Works

1. Switch to the **Auxiliary** tab
2. Select the **❓ Ask** subtab
3. Type your question in the "Your Question" field
4. Click "🚀 Send Question"
5. Wait for the AI response (usually 2-5 seconds)

### What the AI Has Access To

- Your complete project structure and all files
- All project documentation (IGNIS_FILE_SPEC, IGNIS_SCRIPT_API, CAMERA_SYSTEM_DOCS, etc.)
- The current state of your game objects and scenes

### Example Questions

- "How do I create a player controller script?"
- "What's the difference between absolute and relative coordinates?"
- "How do I implement collision detection?"
- "Generate a script template for an enemy AI with random movement"
- "How should I structure a game over scene?"
- "What are best practices for organizing game scripts?"

---

## Using AGENT Mode

The AGENT mode allows the AI to automatically make changes to your project files.

### How It Works

1. Switch to the **Auxiliary** tab
2. Select the **🤖 Agent** subtab
3. Describe what you want the AI to do in the "Task Description" field
4. Click "⚡ Execute Task"
5. Confirm the action in the dialog that appears
6. Review the output and any created/modified files

### Important Safety Notes

⚠️ **Always review AI-generated code before using it in production!**

- The AI may make mistakes or produce suboptimal code
- Test all changes before deploying
- Keep backups of important files
- Use version control so you can easily revert changes
- The AI can create/edit files but cannot delete them (requires manual review)

### Example Tasks

- "Create a 'Player.ignis' script in the scripts folder that handles player movement with WASD keys"
- "Add a pause menu UI screen with resume and quit buttons"
- "Create a settings scene with sliders for audio volume and brightness"
- "Generate a random level generator script that creates platforms"
- "Create an inventory system script with add/remove item functions"
- "Generate a simple enemy patrol script that moves left and right"

### What AGENT Can Do

**CREATE_FILE** - Create new files with any content
```
CREATE_FILE: scripts/MyScript.ignis
function onStart() {
  // Script code here
}
/CREATE_FILE
```

**EDIT_FILE** - Modify existing files
```
EDIT_FILE: scripts/MyScript.ignis
function onStart() {
  // Updated code here
}
/EDIT_FILE
```

**DELETE_FILE** - Flag files for deletion (you review manually)
```
DELETE_FILE: scripts/OldScript.ignis
```

---

## Troubleshooting

### API Key Issues

**"API Key not configured"**
- Make sure you pasted the key correctly in Settings tab
- Check that there are no extra spaces before/after the key
- Try generating a new API key from Google AI Studio

**"API Error: 404" or "Model not found"**
- This error should not occur anymore (we fixed the model)
- If it happens, try generating a fresh API key
- Make sure you're using the latest version of the editor

**"API Error: 429" (Rate Limited)**
- You've exceeded the free tier rate limit
- Wait a few minutes before trying again
- Consider upgrading your Google Cloud plan for higher limits

**"Network error" or "Connection refused"**
- Check your internet connection
- Verify your firewall isn't blocking outbound connections to googleapis.com
- Try again in a few moments

### API Integration Not Working

The current version uses Google Gemini 1.5 Flash API with REST calls. The infrastructure is fully functional.

If you experience issues:

1. **Check your API key** - Paste it again in Settings tab
2. **Check internet connection** - Try accessing google.com in your browser
3. **Check firewall** - Ensure googleapis.com is not blocked
4. **Check API quotas** - Visit [Google Cloud Console](https://console.cloud.google.com/) to verify your API is enabled and quota is available

### AGENT Mode Shows No Output

This can happen if:
- The AI response doesn't include structured file actions
- The response encountered an API error
- Check the "Agent Actions & Results" area for error messages
- Try a simpler task first to test

For detailed error information, check:
1. The status label next to the execute button
2. The output area (may show raw AI response if no actions found)
3. Your project folder's file system (check if files were created)

### Slow Responses

Large project structures can slow down API calls:
- First request with extensive documentation may be slower (5-10 seconds)
- Subsequent requests are typically faster (2-5 seconds)
- Agent mode usually takes longer due to more context being sent
- This is normal and expected

---

## Privacy & Security

- **API Key** stored locally in `ai_settings.json` (only in this project)
- **Project Files** sent to Google's API for processing (necessary for AI context)
- **Never commit** `ai_settings.json` to version control
- **Add to .gitignore** to prevent accidental pushes
- **API calls are HTTPS encrypted** during transmission

Example .gitignore entry:
```
ai_settings.json
```

---

## Best Practices

1. **Start with ASK Mode**
   - Get familiar with how the AI understands your project
   - Build confidence in the quality of responses
   - Use it to understand the engine better

2. **Use Incremental Tasks in AGENT Mode**
   - Break complex tasks into smaller parts
   - Create one script at a time instead of multiple
   - Test each created file before proceeding

3. **Always Review Generated Code**
   - Read through all AI-generated code
   - Check for logical errors and inefficiencies
   - Adapt code to match your project's style

4. **Provide Clear Instructions**
   - More detailed prompts = better results
   - Include specific requirements and constraints
   - Example: "Create a script that moves left and right with detection of screen edges" is better than "Create a movement script"

5. **Test Thoroughly**
   - Test all AI-generated features before deployment
   - Try edge cases and unusual scenarios
   - Ensure the code integrates well with existing code

6. **Use Conversation Context**
   - ASK mode responses include your project documentation
   - The AI knows about the Ignis engine's features
   - You can ask follow-up questions about previous responses

---

## Learning Resources

- [Google Generative AI API Documentation](https://ai.google.dev/docs)
- [Ignis Engine Script API](./IGNIS_SCRIPT_API.md)
- [Script Writing Guide](./IGNIS_SCRIPTS.md)
- [Camera System Documentation](./CAMERA_SYSTEM_DOCS.md)
- [File Format Specification](./IGNIS_FILE_SPEC.md)
