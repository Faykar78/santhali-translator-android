package com.vernova.santhali.translation

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * BPE Tokenizer for IndicTrans2.
 *
 * Reads the HuggingFace tokenizer JSON (tokenizer_src.json / tokenizer_tgt.json)
 * which contains:
 *   - model.vocab: token → id mapping (130K entries)
 *   - model.merges: BPE merge rules (245K entries)
 *   - pre_tokenizer: Metaspace (▁ prefix for word boundaries)
 *   - post_processor: appends </s> (id=2)
 *
 * For encoding:  text → pre-tokenize (▁ split) → BPE merge → vocab lookup → ids
 * For decoding:  ids → reverse vocab → join → strip ▁
 */
class SentencePieceTokenizer {

    companion object {
        private const val TAG = "BPETokenizer"
        private const val SPACE_SYMBOL = "▁"  // U+2581
        private const val UNK_ID = 3
        private const val BOS_ID = 0   // <s>
        private const val EOS_ID = 2   // </s>
        private const val PAD_ID = 1   // <pad>
    }

    private val vocab: Map<String, Int>           // token → id
    private val reverseVocab: Map<Int, String>    // id → token
    private val merges: List<Pair<String, String>> // BPE merge pairs in priority order

    /**
     * Load tokenizer from a HuggingFace tokenizer JSON file.
     */
    constructor(tokenizerJsonFile: File) {
        Log.i(TAG, "Loading tokenizer from ${tokenizerJsonFile.name} (${tokenizerJsonFile.length() / 1024} KB)")
        val json = JSONObject(tokenizerJsonFile.readText())
        val model = json.getJSONObject("model")

        // Load vocab
        val vocabJson = model.getJSONObject("vocab")
        val vocabMap = mutableMapOf<String, Int>()
        for (key in vocabJson.keys()) {
            vocabMap[key] = vocabJson.getInt(key)
        }
        vocab = vocabMap
        reverseVocab = vocabMap.entries.associate { it.value to it.key }

        // Load merges
        val mergesArray = model.getJSONArray("merges")
        val mergesList = mutableListOf<Pair<String, String>>()
        for (i in 0 until mergesArray.length()) {
            val parts = mergesArray.getString(i).split(" ", limit = 2)
            if (parts.size == 2) {
                mergesList.add(parts[0] to parts[1])
            }
        }
        merges = mergesList

        Log.i(TAG, "Loaded vocab=${vocab.size} merges=${merges.size}")
    }

    /**
     * Encode [text] to a list of token IDs.
     * Uses Metaspace pre-tokenization + BPE merging.
     */
    fun encode(text: String): List<Long> {
        if (text.isBlank()) return emptyList()

        // 1. Pre-tokenize: split on spaces, prepend ▁ to each word
        val words = preTokenize(text)
        Log.d(TAG, "Pre-tokenized: $words")

        // 2. For each word, try vocab lookup first, then BPE
        val tokenIds = mutableListOf<Long>()
        for (word in words) {
            // Direct vocab lookup (covers most common words)
            if (word in vocab) {
                tokenIds.add(vocab[word]!!.toLong())
                continue
            }

            // BPE encode
            val subTokens = bpeEncode(word)
            for (sub in subTokens) {
                tokenIds.add((vocab[sub] ?: UNK_ID).toLong())
            }
        }

        // 3. Post-process: append </s>
        tokenIds.add(EOS_ID.toLong())

        Log.d(TAG, "Encoded ${text.take(50)} → ${tokenIds.size} tokens")
        return tokenIds
    }

    /**
     * Decode a list of token IDs back to text.
     */
    fun decode(ids: List<Int>): String {
        val tokens = ids
            .filter { it > 3 }  // Skip special tokens
            .mapNotNull { reverseVocab[it] }
        val joined = tokens.joinToString("")
        // Replace ▁ with space and trim
        return joined.replace(SPACE_SYMBOL, " ").trim()
    }

    // ── Pre-tokenization ────────────────────────────────────────────────────

    /**
     * Metaspace pre-tokenizer: splits on whitespace, prepends ▁ to each token.
     */
    private fun preTokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (word in words) {
            result.add(SPACE_SYMBOL + word)
        }
        return result
    }

    // ── BPE Encoding ────────────────────────────────────────────────────────

    /**
     * Apply BPE merges to a word that wasn't found as a whole token.
     * Returns list of subword tokens.
     */
    private fun bpeEncode(word: String): List<String> {
        // Start with individual characters
        val symbols = word.map { it.toString() }.toMutableList()

        if (symbols.size <= 1) return symbols

        // Build a set of merge pairs for O(1) lookup with priority
        // We iterate merges in order (highest priority first)
        var changed = true
        while (changed && symbols.size > 1) {
            changed = false

            // Find the highest-priority merge that applies
            var bestIdx = -1
            var bestMergeRank = Int.MAX_VALUE

            for (i in 0 until symbols.size - 1) {
                val pair = symbols[i] to symbols[i + 1]
                val rank = getMergeRank(pair)
                if (rank != -1 && rank < bestMergeRank) {
                    bestMergeRank = rank
                    bestIdx = i
                }
            }

            if (bestIdx == -1) break

            // Apply the merge
            val merged = symbols[bestIdx] + symbols[bestIdx + 1]
            symbols[bestIdx] = merged
            symbols.removeAt(bestIdx + 1)
            changed = true
        }

        return symbols
    }

    // Merge rank cache for performance
    private val mergeRankCache = mutableMapOf<Pair<String, String>, Int>()

    private fun getMergeRank(pair: Pair<String, String>): Int {
        return mergeRankCache.getOrPut(pair) {
            merges.indexOf(pair)
        }
    }
}
