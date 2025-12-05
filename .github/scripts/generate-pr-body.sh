#!/bin/bash
#
# Generate PR Body
# This script generates the PR body markdown file with commit and file change information.
#
# Required environment variables:
#   BRANCH - The branch name
#
# Optional environment variables:
#   AI_SUCCESS - Whether AI summary was generated ("true" or "false")
#   AI_SUMMARY - AI-generated summary paragraph
#   AI_FEATURES - AI-generated features section
#   AI_FIXES - AI-generated fixes section
#   AI_DOCS - AI-generated docs section
#   AI_OTHER - AI-generated other changes section
#
# Output:
#   Creates pr_body.md in the current directory
#

set -e

# Ensure BRANCH is set
if [ -z "$BRANCH" ]; then
  echo "Error: BRANCH environment variable is required"
  exit 1
fi

# Fetch master branch for comparison
git fetch origin master:master 2>/dev/null || git fetch origin main:master 2>/dev/null || true

# Get last 5 commits in this branch
COMMITS=$(git log master..HEAD --pretty=format:"- %s (%h)" 2>/dev/null | head -5 || echo "No commits found")
COMMIT_COUNT=$(git rev-list --count master..HEAD 2>/dev/null || echo "0")

# Get changed files count
FILES_CHANGED=$(git diff --name-only master..HEAD 2>/dev/null | wc -l | tr -d ' ')

# Get file changes with status (A=added, M=modified, D=deleted)
FILE_STATUS=$(git diff --name-status master..HEAD 2>/dev/null | sort -t$'\t' -k2 || echo "")

# Function to get status emoji and format filename
format_file() {
  local status="$1"
  local filename="$2"
  
  case "$status" in
    A) echo "🟢 $filename" ;;
    D) echo "🔴 ~~$filename~~" ;;
    M|*) echo "🟡 $filename" ;;
  esac
}

# Build tree structure
build_file_tree() {
  local file_status="$1"
  local output=""
  
  # Parse into arrays
  declare -a files
  declare -A statuses
  
  while IFS=$'\t' read -r status filepath; do
    [ -z "$filepath" ] && continue
    files+=("$filepath")
    statuses["$filepath"]="$status"
  done <<< "$file_status"
  
  # Sort files
  IFS=$'\n' sorted_files=($(sort <<<"${files[*]}")); unset IFS
  
  # Track which directories we've printed
  declare -A printed_dirs
  
  local total=${#sorted_files[@]}
  
  for i in "${!sorted_files[@]}"; do
    local filepath="${sorted_files[$i]}"
    local status="${statuses[$filepath]}"
    
    # Split path into parts
    IFS='/' read -ra parts <<< "$filepath"
    local depth=${#parts[@]}
    
    # Print directory headers if needed
    local current_path=""
    for ((d=0; d<depth-1; d++)); do
      if [ -z "$current_path" ]; then
        current_path="${parts[$d]}"
      else
        current_path="$current_path/${parts[$d]}"
      fi
      
      if [ -z "${printed_dirs[$current_path]}" ]; then
        printed_dirs["$current_path"]=1
        
        # Calculate prefix based on depth
        local prefix=""
        for ((p=0; p<d; p++)); do
          prefix+="│   "
        done
        
        # Check if this is the last item at this level
        local dir_name="${parts[$d]}"
        output+="${prefix}├── 📂 ${dir_name}/"$'\n'
      fi
    done
    
    # Print the file
    local filename="${parts[$((depth-1))]}"
    local prefix=""
    for ((p=0; p<depth-1; p++)); do
      prefix+="│   "
    done
    
    # Check if this is the last file in its directory
    local is_last=0
    if [ $((i+1)) -eq $total ]; then
      is_last=1
    else
      # Check if next file is in same directory
      local next_filepath="${sorted_files[$((i+1))]}"
      local this_dir=$(dirname "$filepath")
      local next_dir=$(dirname "$next_filepath")
      if [ "$this_dir" != "$next_dir" ]; then
        is_last=1
      fi
    fi
    
    local connector="├── "
    if [ $is_last -eq 1 ]; then
      connector="└── "
    fi
    
    local formatted=$(format_file "$status" "$filename")
    output+="${prefix}${connector}${formatted}"$'\n'
  done
  
  # Clean up the tree connectors
  # Replace │ with spaces where we've moved past that level
  echo "$output"
}

# Build the file tree
echo "Building file tree..."
FILE_TREE=$(build_file_tree "$FILE_STATUS")

# If tree is empty, show a message
if [ -z "$FILE_TREE" ]; then
  FILE_TREE="_Nenhum arquivo alterado_"
fi

# More commits indicator
MORE_COMMITS=""
if [ "$COMMIT_COUNT" -gt 5 ] 2>/dev/null; then
  MORE_COMMITS=$'\n'"_...e mais $(($COMMIT_COUNT - 5)) commits_"
fi

LAST_UPDATED=$(TZ='America/Sao_Paulo' date +"%Y-%m-%d %H:%M:%S UTC-3")

# Legend table
LEGEND='| Icon | Significado |
|:----:|-------------|
| 🟢 | Novo |
| 🟡 | Modificado |
| 🔴 | Removido |'

# Use AI summary if available
if [ "$AI_SUCCESS" = "true" ]; then
  # Format sections - only show if not "Nenhuma"
  FEATURES_SECTION=""
  if [ -n "$AI_FEATURES" ] && [ "$AI_FEATURES" != "Nenhuma" ]; then
    FEATURES_SECTION="### ✨ Funcionalidades
$AI_FEATURES
"
  fi
  
  FIXES_SECTION=""
  if [ -n "$AI_FIXES" ] && [ "$AI_FIXES" != "Nenhuma" ]; then
    FIXES_SECTION="### 🐛 Correções
$AI_FIXES
"
  fi
  
  DOCS_SECTION=""
  if [ -n "$AI_DOCS" ] && [ "$AI_DOCS" != "Nenhuma" ]; then
    DOCS_SECTION="### 📚 Documentação
$AI_DOCS
"
  fi
  
  OTHER_SECTION=""
  if [ -n "$AI_OTHER" ] && [ "$AI_OTHER" != "Nenhuma" ]; then
    OTHER_SECTION="### 🔧 Outras Mudanças
$AI_OTHER
"
  fi
  
  cat > pr_body.md <<EOF
## 🤖 Resumo

${AI_SUMMARY}

${FEATURES_SECTION}${FIXES_SECTION}${DOCS_SECTION}${OTHER_SECTION}
---

## 📊 Detalhes

**Branch:** \`${BRANCH}\`  
**Total de Commits:** ${COMMIT_COUNT}  
**Arquivos Alterados:** ${FILES_CHANGED}

## 📝 Últimos Commits

${COMMITS}${MORE_COMMITS}

## 📂 Arquivos Alterados

${LEGEND}

\`\`\`
${FILE_TREE}
\`\`\`

---

### ✅ Checklist
- [ ] Código revisado
- [ ] Testes adicionados/atualizados
- [ ] Documentação atualizada
- [ ] Pronto para merge

---

_🤖 Este PR foi criado automaticamente e é atualizado a cada push._  
_Última atualização: ${LAST_UPDATED}_
EOF
else
  # Fallback to basic summary without AI
  cat > pr_body.md <<EOF
## 🚀 Resumo

**Branch:** \`${BRANCH}\`  
**Total de Commits:** ${COMMIT_COUNT}  
**Arquivos Alterados:** ${FILES_CHANGED}

## 📝 Últimos Commits

${COMMITS}${MORE_COMMITS}

## 📂 Arquivos Alterados

${LEGEND}

\`\`\`
${FILE_TREE}
\`\`\`

---

### ✅ Checklist
- [ ] Código revisado
- [ ] Testes adicionados/atualizados
- [ ] Documentação atualizada
- [ ] Pronto para merge

---

_🤖 Este PR foi criado automaticamente e é atualizado a cada push._  
_Última atualização: ${LAST_UPDATED}_
EOF
fi

echo "✅ Generated pr_body.md successfully"
