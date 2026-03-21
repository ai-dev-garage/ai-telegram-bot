# shellcheck shell=bash
# Sourceable: strip Cursor/IDE rule YAML frontmatter for Claude (.md) installs.
#
# Strips the first "---" block: from an initial line that is exactly "---" through
# the next line that is exactly "---" (inclusive). If line 1 is not "---", the
# entire file is printed unchanged.

strip_cursor_rule_frontmatter() {
  local f=$1
  if [[ ! -f "$f" ]]; then
    echo "strip_cursor_rule_frontmatter: not a file: $f" >&2
    return 1
  fi
  exec 3<"$f"
  local line1
  IFS= read -r line1 <&3 || {
    exec 3<&-
    return 0
  }
  if [[ "$line1" != "---" ]]; then
    printf '%s\n' "$line1"
    cat <&3
    exec 3<&-
    return 0
  fi
  local in_frontmatter=1 line
  while IFS= read -r line <&3 || [[ -n "${line}" ]]; do
    if [[ $in_frontmatter -eq 1 ]]; then
      if [[ "$line" == "---" ]]; then
        in_frontmatter=0
      fi
    else
      printf '%s\n' "$line"
    fi
  done
  exec 3<&-
}
