// noinspection DuplicatedCode

'use strict';

// populated by `initSearchIndex()`
let searchIndex;

// noinspection ThisExpressionReferencesGlobalObjectJS
const isWorker = 'DedicatedWorkerGlobalScope' in this;

if (isWorker) {
  const workerName = self.name;
  // relative to this file
  const searchIndexUrl = workerName === "main" ?
      '../search-index.js' :
      '../' + workerName + '/search-index.js';
  importScripts(searchIndexUrl);
  initSearchIndex();
  addEventListener('message', e => {
    const {query, packageName, moduleName, className} = e.data;
    const results = runSearch(query, packageName, moduleName, className);
    postMessage({query, results});
  });
} else {
  // non-worker environment
  // `pkldoc.js` loads scripts and calls `initSearchIndex()`
}

// Initializes the search index.
function initSearchIndex() {
  // noinspection JSUnresolvedVariable
  const data = JSON.parse(searchData);
  const index = Array(data.length);
  let idx = 0;

  for (const entry of data) {
    const name = entry.name;
    const names = toIndexedNames(entry);
    // 0 -> package, 1 -> module, 2 -> type alias, 3 -> class, 4 -> function, 5 -> property
    const kind = entry.kind;
    const url = entry.url;
    // noinspection JSUnresolvedVariable
    const signature = entry.sig === undefined ? null : entry.sig;
    // noinspection JSUnresolvedVariable
    const parent = entry.parId === undefined ? null : index[entry.parId];
    const level = parent === null ? 0 : parent.parent === null ? 1 : 2;
    const deprecated = entry.deprecated !== undefined;

    index[idx++] = {
      name,
      names,
      kind,
      url,
      signature,
      parent,
      level,
      deprecated,
      // remaining attributes are set by `computeMatchFrom` and hence aren't strictly part of the search index
      matchNameIdx: -1,  // names[matchNameIdx] is the name that matched
      matchStartIdx: -1, // names[matchNameIdx].codePoints[matchStartIdx] is the first code point that matched
      similarity: 0 // number of code points matched relative to total number of code points (between 0.0 and 1.0)
    };
  }

  searchIndex = index;
}

// Runs a search and returns its results.
function runSearch(query, packageName, moduleName, className) {
  const queryCps = query.normalizedCps;
  const queryKind = query.kind;

  let exactMatches = [];
  let classMatches = [];
  let moduleMatches = [];
  let otherMatches = [];

  for (const member of searchIndex) {
    if (queryKind !== null && queryKind !== member.kind) continue;

    if (!isMatch(queryCps, member)) continue;

    if (member.similarity === 1) {
      exactMatches.push(member);
    } else if (moduleName !== null && member.level === 1 && moduleName === member.parent.name) {
      moduleMatches.push(member);
    } else if (moduleName !== null && member.level === 2 && moduleName === member.parent.parent.name) {
      if (className !== null && className === member.parent.name) {
        classMatches.push(member);
      } else {
        moduleMatches.push(member);
      }
    } else {
      otherMatches.push(member);
    }
  }

  // Sorts members best-first.
  function compareMembers(member1, member2) {
    const normDiff = member2.similarity - member1.similarity; // higher is better
    if (normDiff !== 0) return normDiff;

    const lengthDiff = member1.matchNameLength - member2.matchNameLength; // lower is better
    if (lengthDiff !== 0) return lengthDiff;

    const kindDiff = member2.kind - member1.kind; // higher is better
    if (kindDiff !== 0) return kindDiff;

    return member1.matchNameIdx - member2.matchNameIdx; // lower is better
  }

  exactMatches.sort(compareMembers);
  classMatches.sort(compareMembers);
  moduleMatches.sort(compareMembers);
  otherMatches.sort(compareMembers);

  return {exactMatches, classMatches, moduleMatches, otherMatches};
}

// Indexes a member's names.
function toIndexedNames(entry) {
  const result = [];
  result.push(toIndexedName(entry.name));
  // noinspection JSUnresolvedVariable
  const alsoKnownAs = entry.aka;
  if (alsoKnownAs !== undefined) {
    for (const name of alsoKnownAs) {
      result.push(toIndexedName(name));
    }
  }
  return result;
}

// Indexes the given name.
function toIndexedName(name) {
  const characters = Array.from(name);
  const codePoints = Uint32Array.from(characters, ch => ch.codePointAt(0));
  const normalizedCps = toNormalizedCodePoints(characters);
  const wordStarts = toWordStarts(characters);

  return {codePoints, normalizedCps, wordStarts};
}

// Converts a Unicode character array to an array of normalized Unicode code points.
// Normalization turns characters into their base forms, e.g., é into e.
// Since JS doesn't support case folding, `toLocaleLowerCase()` is used instead.
function toNormalizedCodePoints(characters) {
  return Uint32Array.from(characters, ch => ch.normalize('NFD')[0].toLocaleLowerCase().codePointAt(0));
}

// Returns an array of same length as `characters` that for every index, holds the index of the next word start.
// Preconditions:
// characters.length > 0
function toWordStarts(characters) {
  const length = characters.length;
  // -1 is used as 'no next word start exists' -> use signed int array
  const result = length <= 128 ? new Int8Array(length) : new Int16Array(length);

  if (length > 1) {
    let class1 = toCharClass(characters[length - 1]);
    let class2;
    let wordStart = -1;
    for (let idx = length - 1; idx >= 1; idx -= 1) {
      class2 = class1;
      class1 = toCharClass(characters[idx - 1]);
      const diff = class1 - class2;
      // transitions other than uppercase -> other
      if (diff !== 0 && diff !== 3) wordStart = idx;
      result[idx] = wordStart;
      // uppercase -> other
      if (diff === 3) wordStart = idx - 1;
    }
  }

  // first character is always a word start
  result[0] = 0;

  return result;
}


// Partitions characters into uppercase, digit, dot, and other.
function toCharClass(ch) {
  const regexIsUppercase = /\p{Lu}/u
  const regexIsNumericCharacter = /\p{N}/u
  return regexIsUppercase.test(ch) ? 3 : regexIsNumericCharacter.test(ch) ? 2 : ch === '.' ? 1 : 0;
}

// Tests if `queryCps` matches any of `member`'s names.
// If so, records information about the match in `member`.
// Preconditions:
// queryCps.length > 0
function isMatch(queryCps, member) {
  const queryLength = queryCps.length;
  let nameIdx = 0;

  for (const name of member.names) {
    const nameCps = name.normalizedCps;
    const nameLength = nameCps.length;
    const wordStarts = name.wordStarts;
    const maxStartIdx = nameLength - queryLength;

    for (let startIdx = 0; startIdx <= maxStartIdx; startIdx += 1) {
      const matchLength = computeMatchFrom(queryCps, nameCps, wordStarts, startIdx);
      if (matchLength > 0) {
        member.matchNameIdx = nameIdx;
        member.matchStartIdx = startIdx;
        // Treat exact match of last module name component as exact match (similarity == 1).
        // For example, treat "PodSpec" as exact match for "io.k8s.api.core.v1.PodSpec".
        // Because "ps" is considered an exact match for "PodSpec",
        // it is also considered an exact match for "io.k8s.api.core.v1.PodSpec".
        const isExactMatchOfLastModuleNameComponent =
            startIdx > 0 && nameCps[startIdx - 1] === 46 /* '.' */ && matchLength === nameLength - startIdx;
        member.similarity = isExactMatchOfLastModuleNameComponent ? 1 : matchLength / nameLength;
        member.matchNameLength = nameLength;
        return true;
      }
    }

    nameIdx += 1;
  }

  return false;
}

// Tests if the given query matches the given name from `startIdx` on.
// Returns the number of code points matched.
// Word start matches get special treatment.
// For example, `sb` is considered to match all code points of `StringBuilder`.
// Preconditions:
// queryCps.length > 0
// nameCps.length > 0
// wordStarts.length === nameCps.length
// startIdx < nameCps.length
function computeMatchFrom(queryCps, nameCps, wordStarts, startIdx) {
  const queryLength = queryCps.length;
  const nameLength = nameCps.length;
  const beginsWithWordStart = wordStarts[startIdx] === startIdx;

  let queryIdx = 0;
  let matchLength = 0;
  let queryCp = queryCps[0];

  for (let nameIdx = startIdx; nameIdx < nameLength; nameIdx += 1) {
    const nameCp = nameCps[nameIdx];

    if (queryCp === nameCp) {
      matchLength += 1;
    } else { // check for word start match
      if (nameIdx === startIdx || !beginsWithWordStart) return 0;

      const newNameIdx = wordStarts[nameIdx];
      if (newNameIdx === -1) return 0;

      const newNameCp = nameCps[newNameIdx];
      if (queryCp !== newNameCp) return 0;

      matchLength += newNameIdx - nameIdx + 1;
      nameIdx = newNameIdx;
    }

    queryIdx += 1;
    if (queryIdx === queryLength) {
      // in case of a word start match, increase matchLength by number of remaining chars of the last matched word
      const nextIdx = nameIdx + 1;
      if (beginsWithWordStart && nextIdx < nameLength) {
        const nextStart = wordStarts[nextIdx];
        if (nextStart === -1) {
          matchLength += nameLength - nextIdx;
        } else {
          matchLength += nextStart - nextIdx;
        }
      }

      return matchLength;
    }

    queryCp = queryCps[queryIdx];
  }

  return 0;
}
