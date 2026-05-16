// noinspection DuplicatedCode

'use strict';

// Whether the current browser is WebKit.
let isWebKitBrowser;

// The lazily initialized worker for running searches, if any.
let searchWorker = null;

// Tells whether non-worker search is ready for use.
// Only relevant if we determined that we can't use a worker.
let nonWorkerSearchInitialized = false;

// The search div containing search input and search results.
let searchElement;

// The search input element.
let searchInput;

// The package name associated with the current page, if any.
let packageName;

let packageVersion;

// The module name associated with the current page, if any.
let moduleName;

// The class name associated with the current page, if any.
let className;

// Prefix to turn a site-relative URL into a page-relative URL.
// One of "", "../", "../../", etc.
let rootUrlPrefix;

// Prefix to turn a package-relative URL into a page-relative URL.
// One of "", "../", "../../", etc.
let packageUrlPrefix;

// The search result currently selected in the search results list.
let selectedSearchResult = null;

// Initializes the UI.
// Wrapped in a function to avoid execution in tests.
// noinspection JSUnusedGlobalSymbols
function onLoad() {
  isWebKitBrowser = navigator.userAgent.indexOf('AppleWebKit') !== -1;
  searchElement = document.getElementById('search');
  searchInput = document.getElementById('search-input');
  packageName = searchInput.dataset.packageName || null;
  packageVersion = searchInput.dataset.packageVersion || null;
  moduleName = searchInput.dataset.moduleName || null;
  className = searchInput.dataset.className || null;
  rootUrlPrefix = searchInput.dataset.rootUrlPrefix;
  packageUrlPrefix = searchInput.dataset.packageUrlPrefix;

  initExpandTargetMemberDocs();
  initNavigateToMemberPage();
  initToggleMemberDocs();
  initToggleInheritedMembers();
  initCopyModuleUriToClipboard();
  initSearchUi();
}

// If page URL contains a fragment, expand the target member's docs.
// Handled in JS rather than CSS so that target member can still be manually collapsed.
function initExpandTargetMemberDocs() {
  const expandTargetDocs = () => {
    const hash = window.location.hash;
    if (hash.length === 0) return;
    
    const target = document.getElementById(hash.substring(1));
    if (!target) return;

    const member = target.nextElementSibling;
    if (!member || !member.classList.contains('with-expandable-docs')) return;

    expandMemberDocs(member);
  }
  
  window.addEventListener('hashchange', expandTargetDocs);
  expandTargetDocs();
}

// For members that have their own page, navigate to that page when the member's box is clicked.
function initNavigateToMemberPage() {
  const elements = document.getElementsByClassName('with-page-link');
  for (const element of elements) {
    const memberLink = element.getElementsByClassName('name-decl')[0];
    // check if this is actually a link
    // (it isn't if the generator couldn't resolve the link target)
    if (memberLink.tagName === 'A') {
      element.addEventListener('click', (e) => {
        // don't act if user clicked a link
        if (e.target !== null && e.target.closest('a') !== null) return;

        // don't act if user clicked to select some text
        if (window.getSelection().toString()) return;

        memberLink.click();
      });
    }
  }
}

// Expands and collapses member docs.
function initToggleMemberDocs() {
  const elements = document.getElementsByClassName('with-expandable-docs');
  for (const element of elements) {
    element.addEventListener('click', (e) => {
      // don't act if user clicked a link
      if (e.target !== null && e.target.closest('a') !== null) return;

      // don't act if user clicked to select some text
      if (window.getSelection().toString()) return;

      toggleMemberDocs(element);
    });
  }
}

// Shows and hides inherited members.
function initToggleInheritedMembers() {
  const memberGroups = document.getElementsByClassName('member-group');
  for (const group of memberGroups) {
    const button = group.getElementsByClassName('toggle-inherited-members-link')[0];
    if (button !== undefined) {
      const members = group.getElementsByClassName('inherited');
      button.addEventListener('click', () => toggleInheritedMembers(button, members));
    }
  }
}

// Copies the module URI optionally displayed on a module page to the clipboard.
function initCopyModuleUriToClipboard() {
  const copyUriButtons = document.getElementsByClassName('copy-uri-button');

  for (const button of copyUriButtons) {
    const moduleUri = button.previousElementSibling;

    button.addEventListener('click', e => {
      e.stopPropagation();
      const range = document.createRange();
      range.selectNodeContents(moduleUri);
      const selection = getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      try {
        document.execCommand('copy');
      } catch (e) {
      } finally {
        selection.removeAllRanges();
      }
    });
  }
}

// Expands or collapses member docs.
function toggleMemberDocs(memberElem) {
  const comments = memberElem.getElementsByClassName('expandable');
  const icon = memberElem.getElementsByClassName('expandable-docs-icon')[0];
  const isCollapsed = icon.textContent === 'expand_more';

  if (isCollapsed) {
    for (const comment of comments) expandElement(comment);
    icon.textContent = 'expand_less';
  } else {
    for (const comment of comments) collapseElement(comment);
    icon.textContent = 'expand_more';
  }
}

// Expands member docs unless they are already expanded.
function expandMemberDocs(memberElem) {
  const icon = memberElem.getElementsByClassName('expandable-docs-icon')[0];
  const isCollapsed = icon.textContent === 'expand_more';
  
  if (!isCollapsed) return;
  
  const comments = memberElem.getElementsByClassName('expandable');
  for (const comment of comments) expandElement(comment);
  icon.textContent = 'expand_less';
}

// Shows and hides inherited members.
function toggleInheritedMembers(button, members) {
  const isCollapsed = button.textContent === 'show inherited';

  if (isCollapsed) {
    for (const member of members) expandElement(member);
    button.textContent = 'hide inherited';
  } else {
    for (const member of members) collapseElement(member);
    button.textContent = 'show inherited'
  }
}

// Expands an element.
// Done in two steps to make transition work (can't transition from 'hidden').
// For some reason (likely related to removing 'hidden') the transition isn't animated in FF.
// When using timeout() instead of requestAnimationFrame()
// there is *some* animation in FF but still doesn't look right.
function expandElement(element) {
  element.classList.remove('hidden');

  requestAnimationFrame(() => {
    element.classList.remove('collapsed');
  });
}

// Collapses an element.
// Done in two steps to make transition work (can't transition to 'hidden').
function collapseElement(element) {
  element.classList.add('collapsed');

  const listener = () => {
    element.removeEventListener('transitionend', listener);
    element.classList.add('hidden');
  };
  element.addEventListener('transitionend', listener);
}

// Initializes the search UI and sets up delayed initialization of the search engine.
function initSearchUi() {
  // initialize search engine the first time that search input receives focus
  const onFocus = () => {
    searchInput.removeEventListener('focus', onFocus);
    initSearchWorker();
  };
  searchInput.addEventListener('focus', onFocus);

  // clear search when search input loses focus,
  // except if this happens due to a search result being clicked,
  // in which case clearSearch() will be called by the link's click handler,
  // and calling it here would prevent the click handler from firing
  searchInput.addEventListener('focusout', () => {
    if (document.querySelector('#search-results:hover') === null) clearSearch();
  });

  // trigger search when user hasn't typed in a while
  let timeoutId = null;
  // Using anything other than `overflow: visible` for `#search-results`
  // slows down painting significantly in WebKit browsers (at least Safari/Mac).
  // Compensate by using a higher search delay, which is less annoying than a blocking UI.
  const delay = isWebKitBrowser ? 200 : 100;
  searchInput.addEventListener('input', () => {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => triggerSearch(searchInput.value), delay);
  });

  // keyboard shortcut for entering search
  document.addEventListener('keyup', e => {
    // could additionally support '/' like GitHub and Gmail do,
    // but this would require overriding the default behavior of '/' on Firefox
    if (e.key === 's') searchInput.focus();
  });

  // keyboard navigation for search results
  searchInput.addEventListener('keydown', e => {
    const results = document.getElementById('search-results');
    if (results !== null) {
      if (e.key === 'ArrowDown') {
        selectNextResult(results.firstElementChild);
        e.preventDefault();
      } else if (e.key === 'ArrowUp') {
        selectPrevResult(results.firstElementChild);
        e.preventDefault();
      }
    }
  });
  searchInput.addEventListener('keyup', e => {
    if (e.key === 'Enter' && selectedSearchResult !== null) {
      selectedSearchResult.firstElementChild.click();
      clearSearch();
    }
  });
}

// Initializes the search worker.
function initSearchWorker() {
  const workerScriptUrl = rootUrlPrefix + 'scripts/search-worker.js';

  try {
    searchWorker = new Worker(workerScriptUrl, {name: packageName === null ? "main" : packageName + '/' + packageVersion});
    searchWorker.addEventListener('message', e => handleSearchResults(e.data.query, e.data.results));
  } catch (e) {
    // could not initialize worker, presumably because we are a file:/// page and content security policy got in the way
    // fall back to running searches synchronously without a worker
    // this requires loading search related scripts that would otherwise be loaded by the worker

    searchWorker = null;
    let pendingScripts = 3;

    const onScriptLoaded = () => {
      if (--pendingScripts === 0) {
        initSearchIndex();
        nonWorkerSearchInitialized = true;
        if (searchInput.focused) {
          triggerSearch(searchInput.value);
        }
      }
    };
    
    const script1 = document.createElement('script');
    script1.src = (packageName === null ? rootUrlPrefix : packageUrlPrefix) + 'search-index.js';
    script1.async = true;
    script1.onload = onScriptLoaded;
    document.head.append(script1);

    const script2 = document.createElement('script');
    script2.src = rootUrlPrefix;
    script2.async = true;
    script2.onload = onScriptLoaded;
    document.head.append(script2);

    const script3 = document.createElement('script');
    script3.src = workerScriptUrl;
    script3.async = true;
    script3.onload = onScriptLoaded;
    document.head.append(script3);
  }
}

// Updates search results unless they are stale.
function handleSearchResults(query, results) {
  if (query.inputValue !== searchInput.value) return;

  updateSearchResults(renderSearchResults(query, results));
}

// TODO: Should this (or its callers) use requestAnimationFrame() ?
// Removes any currently displayed search results, then displays the given results if non-null.
function updateSearchResults(resultsDiv) {
  selectedSearchResult = null;

  const oldResultsDiv = document.getElementById('search-results');
  if (oldResultsDiv !== null) {
    searchElement.removeChild(oldResultsDiv);
  }

  if (resultsDiv != null) {
    searchElement.append(resultsDiv);
    selectNextResult(resultsDiv.firstElementChild);
  }
}

// Returns the module of the given member, or `null` if the given member is a module.
function getModule(member) {
  switch (member.level) {
    case 0:
      return null;
    case 1:
      return member.parent;
    case 2:
      return member.parent.parent;
  }
}

// Triggers a search unless search input is invalid or incomplete.
function triggerSearch(inputValue) {
  const query = parseSearchInput(inputValue);
  if (!isActionableQuery(query)) {
    handleSearchResults(query, null);
    return;
  }

  if (searchWorker !== null) {
    searchWorker.postMessage({query, packageName, moduleName, className});
  } else if (nonWorkerSearchInitialized) {
    const results = runSearch(query, packageName, moduleName, className);
    handleSearchResults(query, results);
  }
}

// Tells if the given Unicode character is a whitespace character.
function isWhitespace(ch) {
  const cp = ch.codePointAt(0);
  if (cp >= 9 && cp <= 13 || cp === 32 || cp === 133 || cp === 160) return true;
  if (cp < 5760) return false;
  return cp === 5760 || cp >= 8192 && cp <= 8202
      || cp === 8232 || cp === 8233 || cp === 8239 || cp === 8287 || cp === 12288;
}

// Trims the given Unicode characters.
function trim(chars) {
  const length = chars.length;
  let startIdx, endIdx;

  for (startIdx = 0; startIdx < length; startIdx += 1) {
    if (!isWhitespace(chars[startIdx])) break;
  }
  for (endIdx = chars.length - 1; endIdx > startIdx; endIdx -= 1) {
    if (!isWhitespace(chars[endIdx])) break;
  }
  return chars.slice(startIdx, endIdx + 1);
}

// Parses the user provided search input.
// Preconditions:
// inputValue !== ''
function parseSearchInput(inputValue) {
  const chars = trim(Array.from(inputValue));
  const char0 = chars[0]; // may be undefined
  const char1 = chars[1]; // may be undefined
  const prefix = char1 === ':' ? char0 + char1 : null;
  const kind =
      prefix === null ? null :
          char0 === 'm' ? 1 :
              char0 === 't' ? 2 :
                  char0 === 'c' ? 3 :
                      char0 === 'f' ? 4 :
                          char0 === 'p' ? 5 :
                              undefined;
  const unprefixedChars = kind !== null && kind !== undefined ?
      trim(chars.slice(2, chars.length)) :
      chars;
  const normalizedCps = toNormalizedCodePoints(unprefixedChars);
  return {inputValue, prefix, kind, normalizedCps};
}

// Converts a Unicode character array to an array of normalized Unicode code points.
// Normalization turns characters into their base forms, e.g., é into e.
// Since JS doesn't support case folding, `toLocaleLowerCase()` is used instead.
// Note: Keep in sync with same function in search-worker.js.
function toNormalizedCodePoints(characters) {
  return Uint32Array.from(characters, ch => ch.normalize('NFD')[0].toLocaleLowerCase().codePointAt(0));
}

// Tells if the given query is valid and long enough to be worth running.
// Prefixed queries require fewer minimum characters than unprefixed queries.
// This avoids triggering a search while typing a prefix yet still enables searching for single-character names.
// For example, `p:e` finds `pkl.math#E`.
function isActionableQuery(query) {
  const kind = query.kind;
  const queryCps = query.normalizedCps;
  return kind !== undefined && (kind !== null && queryCps.length > 0 || queryCps.length > 1);
}

// Renders the given search results for the given query.
// Preconditions:
// isActionableQuery(query) ? results !== null : results === null
function renderSearchResults(query, results) {
  const resultsDiv = document.createElement('div');
  resultsDiv.id = 'search-results';
  const ul = document.createElement('ul');
  resultsDiv.append(ul);

  if (results === null) {
    if (query.kind !== undefined) return null;

    const li = document.createElement('li');
    li.className = 'heading';
    li.textContent = 'Unknown search prefix. Use one of <b>m:</b> (module), <b>c:</b> (class), <b>f:</b> (function), or <b>p:</b> (property).';
    ul.append(li);
    return resultsDiv;
  }

  const {exactMatches, classMatches, moduleMatches, otherMatches} = results;

  if (exactMatches.length + classMatches.length + moduleMatches.length + otherMatches.length === 0) {
    renderHeading('No results found', ul);
    return resultsDiv;
  }

  if (exactMatches.length > 0) {
    renderHeading('Top hits', ul);
    renderMembers(query.normalizedCps, exactMatches, ul);
  }
  if (classMatches.length > 0) {
    renderHeading('Class', ul, className);
    renderMembers(query.normalizedCps, classMatches, ul);
  }
  if (moduleMatches.length > 0) {
    renderHeading('Module', ul, moduleName);
    renderMembers(query.normalizedCps, moduleMatches, ul);
  }
  if (otherMatches.length > 0) {
    renderHeading('Other results', ul);
    renderMembers(query.normalizedCps, otherMatches, ul);
  }

  return resultsDiv;
}

// Adds a heading such as `Top matches` to the search results list.
function renderHeading(title, ul, name = null) {
  const li = document.createElement('li');
  li.className = 'heading';
  li.append(title);
  if (name != null) {
    li.append(' ');
    li.append(span('heading-name', name))
  }
  ul.append(li);
}

// Adds matching members to the search results list.
function renderMembers(queryCps, members, ul) {
  for (const member of members) {
    ul.append(renderMember(queryCps, member));
  }
}

// Renders a member to be added to the search result list.
function renderMember(queryCps, member) {
  const result = document.createElement('li');
  result.className = 'result';
  if (member.deprecated) result.className = 'deprecated';

  const link = document.createElement('a');
  result.append(link);

  link.href = (packageName === null ? rootUrlPrefix : packageUrlPrefix) + member.url;
  link.addEventListener('mousedown', () => selectResult(result));
  link.addEventListener('click', clearSearch);

  const keyword = getKindKeyword(member.kind);
  // noinspection JSValidateTypes (IntelliJ bug?)
  if (keyword !== null) {
    link.append(span('keyword', keyword), ' ');
  }

  // prefix with class name if a class member
  if (member.level === 2) {
    link.append(span("context", member.parent.name + '.'));
  }

  const name = span('result-name');
  if (member.matchNameIdx === 0) { // main name matched
    highlightMatch(queryCps, member.names[0], member.matchStartIdx, name);
  } else { // aka name matched
    name.append(member.name);
  }
  link.append(name);

  if (member.signature !== null) {
    link.append(member.signature);
  }

  if (member.matchNameIdx > 0) { // aka name matched
    link.append(' ');
    const aka = span('aka');
    aka.append('(known as: ');
    const name = span('aka-name');
    highlightMatch(queryCps, member.names[member.matchNameIdx], member.matchStartIdx, name);
    aka.append(name, ')');
    link.append(aka);
  }

  // add module name if not a module
  const module = getModule(member);
  if (module !== null) {
    link.append(' ', span('context', '(' + module.name + ')'));
  }

  return result;
}

// Returns the keyword for the given member kind.
function getKindKeyword(kind) {
  switch (kind) {
    case 0:
      return "package";
    case 1:
      return "module";
    case 2:
      return "typealias";
    case 3:
      return "class";
    case 4:
      return "function";
    case 5:
      // properties have no keyword
      return null;
  }
}

// Highlights the matching characters in a member name.
// Preconditions:
// queryCps.length > 0
// computeMatchFrom(queryCps, name.normalizedCps, name.wordStarts, matchStartIdx)
function highlightMatch(queryCps, name, matchStartIdx, parentElem) {
  const queryLength = queryCps.length;
  const codePoints = name.codePoints;
  const nameCps = name.normalizedCps;
  const nameLength = nameCps.length;
  const wordStarts = name.wordStarts;

  let queryIdx = 0;
  let queryCp = queryCps[0];
  let startIdx = matchStartIdx;

  if (startIdx > 0) {
    parentElem.append(String.fromCodePoint(...codePoints.subarray(0, startIdx)));
  }

  for (let nameIdx = startIdx; nameIdx < nameLength; nameIdx += 1) {
    const nameCp = nameCps[nameIdx];

    if (queryCp !== nameCp) {
      const newNameIdx = wordStarts[nameIdx];
      parentElem.append(
          span('highlight', String.fromCodePoint(...codePoints.subarray(startIdx, nameIdx))));
      startIdx = newNameIdx;
      parentElem.append(String.fromCodePoint(...codePoints.subarray(nameIdx, newNameIdx)));
      nameIdx = newNameIdx;
    }

    queryIdx += 1;
    if (queryIdx === queryLength) {
      parentElem.append(
          span('highlight', String.fromCodePoint(...codePoints.subarray(startIdx, nameIdx + 1))));
      if (nameIdx + 1 < nameLength) {
        parentElem.append(String.fromCodePoint(...codePoints.subarray(nameIdx + 1, nameLength)));
      }
      return;
    }

    queryCp = queryCps[queryIdx];
  }

  throw 'Precondition violated: `computeMatchFrom()`';
}

// Creates a span element.
function span(className, text = null) {
  const result = document.createElement('span');
  result.className = className;
  result.textContent = text;
  return result;
}

// Creates a text node.
function text(content) {
  return document.createTextNode(content);
}

// Navigates to the next member entry in the search results list, skipping headings.
function selectNextResult(ul) {
  let next = selectedSearchResult === null ? ul.firstElementChild : selectedSearchResult.nextElementSibling;
  while (next !== null) {
    if (!next.classList.contains('heading')) {
      selectResult(next);
      scrollIntoView(next, {
        behavior: 'instant', // better for keyboard navigation
        scrollMode: 'if-needed',
        block: 'nearest',
        inline: 'nearest',
      });
      return;
    }
    next = next.nextElementSibling;
  }
}

// Navigates to the previous member entry in the search results list, skipping headings.
function selectPrevResult(ul) {
  let prev = selectedSearchResult === null ? ul.lastElementChild : selectedSearchResult.previousElementSibling;
  while (prev !== null) {
    if (!prev.classList.contains('heading')) {
      selectResult(prev);
      const prev2 = prev.previousElementSibling;
      // make any immediately preceding heading visible as well (esp. important for first heading)
      const scrollTo = prev2 !== null && prev2.classList.contains('heading') ? prev2 : prev;
      scrollIntoView(scrollTo, {
        behavior: 'instant', // better for keyboard navigation
        scrollMode: 'if-needed',
        block: 'nearest',
        inline: 'nearest',
      });
      return;
    }
    prev = prev.previousElementSibling;
  }
}

// Selects the given entry in the search results list.
function selectResult(li) {
  if (selectedSearchResult !== null) {
    selectedSearchResult.classList.remove('selected');
  }
  li.classList.add('selected');
  selectedSearchResult = li;
}

// Clears the search input and hides/removes the search results list.
function clearSearch() {
  searchInput.value = '';
  updateSearchResults(null);
}

const updateRuntimeDataWith = (buildAnchor) => (fragmentId, entries) => {
  if (!entries) return;
  const fragment = document.createDocumentFragment();
  let first = true;
  for (const entry of entries) {
    const a = document.createElement("a");
    buildAnchor(entry, a);
    if (first) {
      first = false;
    } else {
      fragment.append(", ");
    }
    fragment.append(a);
  }

  const element = document.getElementById(fragmentId);
  element.append(fragment);
  element.classList.remove("hidden"); // dd
  element.previousElementSibling.classList.remove("hidden"); // dt
}

// Functions called by JS data scripts.
// noinspection JSUnusedGlobalSymbols
const runtimeData = {
  knownVersions: (versions, myVersion) => {
    updateRuntimeDataWith((entry, anchor) => {
      const { text, href } = entry;
      anchor.textContent = text;
      // noinspection JSUnresolvedReference
      if (text === myVersion) {
        anchor.className = "current-version";
      } else if (href) {
        anchor.href = href;
      }
    })("known-versions", versions);
  },
  knownUsagesOrSubtypes: updateRuntimeDataWith((entry, anchor) => {
    const { text, href } = entry;
    anchor.textContent = text;
    // noinspection JSUnresolvedReference
    anchor.textContent = text;
    if (href) {
      anchor.href = href;
    }
  }),
}
