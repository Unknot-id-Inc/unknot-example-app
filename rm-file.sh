#!/bin/bash
git filter-branch --force --index-filter \
  'git rm --cached --ignore-unmatch app/google-services.json' \
  --prune-empty --tag-name-filter cat -- --all
