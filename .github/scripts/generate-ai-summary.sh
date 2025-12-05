#!/bin/bash
#
# Generate AI PR Summary using Groq API
# This script calls the Groq API to generate an AI-powered PR summary.
#
# Required environment variables:
#   BRANCH - The branch name
#   GROQ_API_KEY - Groq API key for authentication
#   GITHUB_OUTPUT - GitHub Actions output file path
#
# Output:
#   Writes to GITHUB_OUTPUT:
#     ai_title - AI-generated PR title
#     ai_summary - AI-generated summary paragraph
#     ai_features - AI-generated features section (or "Nenhuma")
#     ai_fixes - AI-generated fixes section (or "Nenhuma")
#     ai_docs - AI-generated docs section (or "Nenhuma")
#     ai_other - AI-generated other changes section (or "Nenhuma")
#     ai_success - "true" if successful, "false" otherwise
#

set -e

# Ensure required variables are set
if [ -z "$BRANCH" ]; then
  echo "Error: BRANCH environment variable is required"
  exit 1
fi

if [ -z "$GROQ_API_KEY" ]; then
  echo "Warning: GROQ_API_KEY not set, skipping AI summary"
  echo "ai_success=false" >> "$GITHUB_OUTPUT"
  exit 0
fi

if [ -z "$GITHUB_OUTPUT" ]; then
  echo "Error: GITHUB_OUTPUT environment variable is required"
  exit 1
fi

# Fetch master branch for comparison
git fetch origin master:master 2>/dev/null || git fetch origin main:master 2>/dev/null || true

# Collect context for AI
COMMIT_MESSAGES=$(git log master..HEAD --pretty=format:"%s" 2>/dev/null | head -20 || echo "No commits")
FILES_CHANGED=$(git diff --name-only master..HEAD 2>/dev/null | head -30 || echo "No files")
DIFF_STAT=$(git diff master..HEAD --stat 2>/dev/null | head -30 || echo "No diff stats")

# Build and escape prompt for JSON
echo "Building AI prompt..."
PROMPT=$(printf '%s\n' \
  "Analyze these git changes and generate a PR summary in Portuguese (pt-BR)." \
  "" \
  "Branch: $BRANCH" \
  "" \
  "Commit messages:" \
  "$COMMIT_MESSAGES" \
  "" \
  "Files changed:" \
  "$FILES_CHANGED" \
  "" \
  "Diff stats:" \
  "$DIFF_STAT" \
  "" \
  "INSTRUCTIONS:" \
  "1. Generate a concise PR title (max 60 chars, Portuguese, human-readable)" \
  "2. Write a 2-3 sentence summary paragraph explaining WHAT and WHY" \
  "3. Group changes by conventional commit types and summarize (don't list raw commits):" \
  "   - FEATURES: New functionality (from feat: commits)" \
  "   - FIXES: Bug fixes (from fix: commits)" \
  "   - DOCS: Documentation changes (from docs: commits)" \
  "   - OTHER: Everything else (chore, refactor, style, test, ci, build, perf)" \
  "4. For each category, write 1-3 bullet points summarizing the changes in that category" \
  "5. If a category has no changes, write 'Nenhuma'" \
  "" \
  "FORMAT YOUR RESPONSE EXACTLY AS (keep the labels in English, content in Portuguese):" \
  "TITLE: <título aqui>" \
  "SUMMARY: <parágrafo resumo aqui>" \
  "FEATURES: <bullet points ou 'Nenhuma'>" \
  "FIXES: <bullet points ou 'Nenhuma'>" \
  "DOCS: <bullet points ou 'Nenhuma'>" \
  "OTHER: <bullet points ou 'Nenhuma'>" \
  | jq -Rs .)

echo "Prompt built successfully (length: ${#PROMPT} chars)"

# Call Groq API
echo "Calling Groq API..."
set +e  # Temporarily disable exit on error

RESPONSE=$(curl -s -w "|||HTTP_STATUS:%{http_code}|||" https://api.groq.com/openai/v1/chat/completions \
  -H "Authorization: Bearer $GROQ_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"llama-3.3-70b-versatile\",
    \"messages\": [
      {
        \"role\": \"system\",
        \"content\": \"You are a code review assistant. Analyze git changes and create concise, professional PR summaries in Portuguese. Always follow the exact output format requested. For bullet points, use '- ' prefix.\"
      },
      {
        \"role\": \"user\",
        \"content\": $PROMPT
      }
    ],
    \"temperature\": 0.5,
    \"max_tokens\": 600
  }" 2>&1)

CURL_EXIT=$?
set -e  # Re-enable exit on error

# Extract HTTP status and response body
HTTP_STATUS=$(echo "$RESPONSE" | grep -o "HTTP_STATUS:[0-9]*" | cut -d: -f2)
RESPONSE_BODY=$(echo "$RESPONSE" | sed 's/|||HTTP_STATUS:[0-9]*|||//')

echo "Curl exit code: $CURL_EXIT"
echo "HTTP status: $HTTP_STATUS"
echo "Response length: ${#RESPONSE_BODY} chars"

# Check if API call succeeded
if [ $CURL_EXIT -eq 0 ] && [ "$HTTP_STATUS" = "200" ]; then
  AI_CONTENT=$(echo "$RESPONSE_BODY" | jq -r '.choices[0].message.content // empty')
  
  if [ -n "$AI_CONTENT" ]; then
    echo "✅ AI summary generated successfully"
    echo "$AI_CONTENT"
    
    # Parse AI response - extract each section
    PR_TITLE=$(echo "$AI_CONTENT" | grep "^TITLE:" | sed 's/^TITLE: *//' | head -1)
    PR_SUMMARY=$(echo "$AI_CONTENT" | grep "^SUMMARY:" | sed 's/^SUMMARY: *//' | head -1)
    
    # Extract multi-line sections (from label to next label or end)
    # Using awk to handle multi-line content
    FEATURES=$(echo "$AI_CONTENT" | awk '/^FEATURES:/{flag=1; sub(/^FEATURES: */, ""); if($0) print; next} /^(FIXES|DOCS|OTHER):/{flag=0} flag{print}' | sed '/^$/d')
    FIXES=$(echo "$AI_CONTENT" | awk '/^FIXES:/{flag=1; sub(/^FIXES: */, ""); if($0) print; next} /^(DOCS|OTHER):/{flag=0} flag{print}' | sed '/^$/d')
    DOCS=$(echo "$AI_CONTENT" | awk '/^DOCS:/{flag=1; sub(/^DOCS: */, ""); if($0) print; next} /^OTHER:/{flag=0} flag{print}' | sed '/^$/d')
    OTHER=$(echo "$AI_CONTENT" | awk '/^OTHER:/{flag=1; sub(/^OTHER: */, ""); if($0) print; next} flag{print}' | sed '/^$/d')
    
    # Default to "Nenhuma" if empty
    [ -z "$FEATURES" ] && FEATURES="Nenhuma"
    [ -z "$FIXES" ] && FIXES="Nenhuma"
    [ -z "$DOCS" ] && DOCS="Nenhuma"
    [ -z "$OTHER" ] && OTHER="Nenhuma"
    
    # Save to output using delimiters for multi-line values
    {
      echo "ai_title=$PR_TITLE"
      echo "ai_summary=$PR_SUMMARY"
      
      # Use heredoc delimiter for multi-line values
      echo "ai_features<<FEATURES_EOF"
      echo "$FEATURES"
      echo "FEATURES_EOF"
      
      echo "ai_fixes<<FIXES_EOF"
      echo "$FIXES"
      echo "FIXES_EOF"
      
      echo "ai_docs<<DOCS_EOF"
      echo "$DOCS"
      echo "DOCS_EOF"
      
      echo "ai_other<<OTHER_EOF"
      echo "$OTHER"
      echo "OTHER_EOF"
      
      echo "ai_success=true"
    } >> "$GITHUB_OUTPUT"
    
    echo "Title: $PR_TITLE"
    echo "Summary: $PR_SUMMARY"
    echo "Features: $FEATURES"
    echo "Fixes: $FIXES"
    echo "Docs: $DOCS"
    echo "Other: $OTHER"
  else
    echo "⚠️ AI response was empty, using fallback"
    echo "API Response: $RESPONSE_BODY"
    echo "ai_success=false" >> "$GITHUB_OUTPUT"
  fi
else
  echo "⚠️ Groq API call failed, using fallback"
  echo "Curl exit code: $CURL_EXIT"
  echo "HTTP status: $HTTP_STATUS"
  echo "Response body: $RESPONSE_BODY"
  echo "ai_success=false" >> "$GITHUB_OUTPUT"
fi
