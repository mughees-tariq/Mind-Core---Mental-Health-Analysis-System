from flask import Flask, request, jsonify
from flask_cors import CORS
from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch
import numpy as np
from datetime import datetime
import logging
import os
import json
import re
from dotenv import load_dotenv

#  environment variables (.env file)
load_dotenv()

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Initialize Flask
app = Flask(__name__)
CORS(app)

# Global variables
model = None
tokenizer = None
classes = None
device = None
hf_api_token = None


def load_model():
    #Loading BERT model, tokenizer, and class labels
    global model, tokenizer, classes, device
    
    try:
        logger.info("🔄 Loading BERT model...")
        
        # Set device 
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        logger.info(f"📱 Using device: {device}")
        
        # Get current directory
        model_dir = os.path.dirname(os.path.abspath(__file__))
        
        # Load tokenizer
        logger.info("🔄 Loading tokenizer...")
        tokenizer = AutoTokenizer.from_pretrained(model_dir)
        logger.info("✅ Tokenizer loaded!")
        
        # Load model
        logger.info("🔄 Loading model weights...")
        model = AutoModelForSequenceClassification.from_pretrained(model_dir)
        model.to(device)
        model.eval()  # Set to evaluation mode
        logger.info("✅ Model loaded!")
        
        # Load class labels
        logger.info("🔄 Loading class labels...")
        classes_path = os.path.join(model_dir, "classes.npy")
        classes = np.load(classes_path, allow_pickle=True)
        logger.info(f"✅ Classes loaded: {classes}")
        
        return True
        
    except Exception as e:
        logger.error(f"❌ Error loading model: {e}")
        import traceback
        traceback.print_exc()
        return False


def load_gemini():
    #Loading Hugging Face API token for report generation
    global hf_api_token
    
    try:
        token = os.getenv("HF_API_TOKEN")
        if not token:
            logger.warning("⚠️ HF_API_TOKEN not found in .env file. Report generation disabled.")
            return False
        
        hf_api_token = token
        logger.info("✅ Hugging Face API token loaded successfully!")
        return True
        
    except Exception as e:
        logger.error(f"❌ Error loading HF token: {e}")
        return False


# Severity mapping
SEVERITY_MAP = {
    'Normal': 'Low',
    'Stress': 'Moderate',
    'Anxiety': 'Moderate',
    'Depression': 'High',
    'Bipolar': 'High',
    'Personality disorder': 'High',
    'Suicidal': 'Critical'
}

# Color codes for UI
COLOR_MAP = {
    'Normal': '#4CAF50',
    'Stress': '#FF9800',
    'Anxiety': '#FF9800',
    'Depression': '#F44336',
    'Bipolar': '#F44336',
    'Personality disorder': '#F44336',
    'Suicidal': '#9C27B0'
}

# TEXT ANALYSIS ENGINE


#  Negation Detection 
# Single-word negation tokens
NEGATION_SINGLES = {
    'not', 'no', 'never', 'dont', "don't", 'doesnt', "doesn't",
    'didnt', "didn't", 'isnt', "isn't", 'wasnt', "wasn't",
    'wont', "won't", 'cant', "can't", 'cannot', 'hardly',
    'barely', 'without', 'none', 'nothing', 'neither', 'nor',
}
# Multi-word negation phrases (checked as substrings)
NEGATION_PHRASES = ['no longer', 'not really', 'not at all', 'far from', 'free from',
                    'used to be', 'used to feel', 'used to have']
NEGATION_WINDOW = 4  # words before a keyword to scan for negation

# Words that CANCEL a preceding negation (double-negative = reinforcement)
# e.g. "can't STOP worrying" → still worried,  "not ONLY depressed" → still depressed
REINFORCEMENT_WORDS = {
    'stop', 'help', 'escape', 'shake', 'get', 'rid', 'overcome',
    'handle', 'control', 'avoid', 'only', 'forget',
}

#  Contrast Conjunctions 
# Second clause after these words typically carries greater semantic weight
CONTRAST_WORDS = {
    'but', 'however', 'although', 'though', 'yet', 'still',
    'nevertheless', 'despite', 'except', 'instead', 'whereas',
}

#  Per-class Configuration (general, no special-case if/else) 
# weakness  : 0–1  how often the model under-predicts this class
# safety    : higher = more dangerous to miss (Suicidal = 10)
# confused_with : classes the model frequently misclassifies *this* class as
CLASS_CONFIG = {
    'Normal':               {'weakness': 0.0, 'safety': 0,  'confused_with': []},
    'Stress':               {'weakness': 0.7, 'safety': 1,  'confused_with': ['Normal']},
    'Anxiety':              {'weakness': 0.3, 'safety': 2,  'confused_with': ['Suicidal']},
    'Depression':           {'weakness': 0.3, 'safety': 3,  'confused_with': ['Normal']},
    'Bipolar':              {'weakness': 0.9, 'safety': 2,  'confused_with': ['Depression']},
    'Personality disorder': {'weakness': 0.9, 'safety': 2,  'confused_with': ['Depression']},
    'Suicidal':             {'weakness': 0.1, 'safety': 10, 'confused_with': []},
}

#  Expanded Metaphor Replacements 
METAPHOR_REPLACEMENTS = [
    #  Stress idioms 
    (re.compile(r'\b(deadline|work|job|school|exam|boss|traffic|waiting|homework)s?\b.*\b(kill(?:ing|s)?)\s+me\b', re.I),
     r'\1 is extremely stressful for me'),
    (re.compile(r'\b(kill(?:ing|s)?)\s+me\b(?!.*\b(suicid|die|death|end.{0,5}life|no.{0,8}point)\b)', re.I),
     'is extremely overwhelming for me'),
    (re.compile(r'\bdying\s+(?:to|for)\b', re.I), 'really wanting to'),
    (re.compile(r'\bkill(?:ing)?\s+it\b', re.I), 'doing great at it'),
    (re.compile(r'\bpulling\s+(?:my\s+)?hair\s+out\b', re.I), 'extremely stressed'),
    (re.compile(r'\bat\s+(?:my|the)\s+(?:breaking|tipping)\s+point\b', re.I),
     'extremely stressed and overwhelmed'),
    (re.compile(r'\bburning\s+(?:the\s+)?candle\s+at\s+both\s+ends\b', re.I),
     'overworked and exhausted'),
    (re.compile(r'\bweight\s+(?:of\s+)?(?:the\s+)?world\s+(?:is\s+)?on\s+(?:my\s+)?shoulders?\b', re.I),
     'feeling extremely pressured and stressed'),
    (re.compile(r'\bhead(?:s?)?\s+(?:is\s+)?(?:going\s+to\s+|gonna\s+)?explod', re.I),
     'extremely stressed'),
    (re.compile(r'\brunning\s+on\s+(?:fumes|empty)\b', re.I), 'completely exhausted and stressed'),
    #  Anxiety idioms 
    (re.compile(r'\bbutterfli?e?s?\s+in\s+(?:my\s+)?stomach\b', re.I),
     'feeling very nervous and anxious'),
    (re.compile(r'\bwalking\s+on\s+eggshells?\b', re.I), 'feeling very anxious and tense'),
    (re.compile(r'\bheart\s+(?:in\s+)?(?:my\s+)?throat\b', re.I), 'feeling extreme anxiety'),
    (re.compile(r'\bon\s+pins?\s+and\s+needles?\b', re.I), 'feeling very anxious'),
    (re.compile(r'\bknot\s+in\s+(?:my\s+)?stomach\b', re.I), 'feeling very anxious'),
    #  Depression idioms 
    (re.compile(r'\b(?:dark|black)\s+(?:cloud|hole|tunnel|pit|void)\b', re.I),
     'deep sadness and hopelessness'),
    (re.compile(r'\brock\s+bottom\b', re.I), 'at my lowest point emotionally feeling depressed'),
    (re.compile(r'\b(?:drowning|sinking)\s+in\s+(?:my\s+)?(?:sorrow|sadness|feelings?|emotions?|grief|tears)\b', re.I),
     'overwhelmed by deep sadness and depression'),
    (re.compile(r'\bempty\s+shell\b', re.I), 'feeling completely empty and numb inside'),
    (re.compile(r'\bgoing\s+through\s+the\s+motions?\b', re.I), 'feeling numb and disconnected from life'),
    (re.compile(r'\blight\s+at\s+(?:the\s+)?end\s+of\s+(?:the\s+)?tunnel\b', re.I),
     'a small sense of hope amid depression'),
    #  Bipolar idioms 
    (re.compile(r'\b(?:emotional\s+)?roller\s*coaster\b', re.I), 'experiencing intense mood swings'),
    (re.compile(r'\bon\s+top\s+of\s+the\s+world\s+(?:one|then|and|but)\b', re.I),
     'experiencing extreme mood swings between highs and lows'),
]

#  Expanded Keyword Signals 
KEYWORD_SIGNALS = {
    'Stress': {
        'strong': [
            'stressed', 'stress', 'burnout', 'burned out', 'burnt out', 'overwork',
            'overworked', 'overwhelmed', 'too much pressure', 'workload', 'deadline',
            'exhausted', 'under pressure', 'cant cope', "can't cope", 'so much to do',
            'stretched thin', 'stressing me', 'piling work', 'stressful', 'swamped',
            'crunch time', 'running on empty', 'running on fumes', 'at my wits end',
            'no work life balance', 'pulling all nighters', 'juggling too much',
            'suffocating workload', 'work piling up',
        ],
        'moderate': [
            'tired', 'busy', 'pressure', 'tense', 'frustrated', 'irritable',
            'drained', 'demanding', 'hectic', 'overloaded', 'no time', 'restless',
            'hate commuting', 'hate my job', 'tension headache', 'clenching',
            'grinding my teeth', 'losing sleep over', 'agitated', 'snapping at',
            'short temper', 'cant focus', 'distracted', 'frazzled', 'worn out',
            'under the gun', 'racing against time',
        ],
    },
    'Bipolar': {
        'strong': [
            'mood swings', 'manic', 'mania', 'bipolar', 'highs and lows',
            'euphoric', 'euphoria', 'rapid cycling', 'elevated mood', 'hypomania',
            'feel on top of the world', 'extremely happy to extremely sad',
            'happy to sad within', 'happy then sad', 'invincible',
            'one day up the next down', 'manic episode', 'depressive episode',
            'mixed episode', 'mood stabilizer', 'lithium',
            'didnt sleep for days', 'spending spree', 'feel like a god',
            'unstoppable energy then crash', 'extreme highs', 'extreme lows',
            'highs to lows', 'highs to extreme', 'euphoria to despair',
            'sad for no reason', 'cry for no reason', 'angry for no reason',
        ],
        'moderate': [
            'up and down', 'mood changes', 'unstable mood', 'emotional rollercoaster',
            'one moment', 'happy to sad', 'happy next', 'energy peaks',
            'cant control my mood', 'impulsive spending', 'grandiose', 'swings',
            'swinging', 'cycling mood', 'alternating', 'extremely sad', 'extremely happy',
            'up one minute down', 'emotions are all over', 'one day', 'the next i',
            'suddenly depressed', 'suddenly happy', 'suddenly sad',
            'suddenly extremely sad', 'extreme energy',
            'crash after', 'went from happy', 'went from sad', 'unpredictable moods',
            'happy but then', 'happy then suddenly', 'sad then happy',
        ],
    },
    'Personality disorder': {
        'strong': [
            'personality disorder', 'borderline', 'bpd', 'dissociat', 'identity crisis',
            'feel like a different person', 'dont know who i am', 'splitting',
            'fear of abandonment', 'unstable relationships', 'relationships are unstable',
            'empty inside always', 'narcissis', 'antisocial behavior',
            'avoidant personality', 'intense fear of abandonment',
            'different person', 'cannot control my behavior', 'cant control my behavior',
            'change personalit', 'no real sense of who', 'histrionic',
            'paranoid personality', 'dependent personality', 'schizoid',
            'i dont feel real', 'derealization', 'depersonalization',
            'everyone leaves me', 'push people away', 'pushing people away',
        ],
        'moderate': [
            'identity', 'control my behavior', 'intense relationships',
            'abandonment', 'self image', 'impulsive', 'emotional instability',
            'black and white thinking', 'detached', 'who i am', 'sense of self',
            'not myself', 'erratic behavior', 'empty all the time', 'no sense of self',
            'wear different masks', 'chaos in relationships', 'love hate relationship',
            'intense emotions about people', 'people see me differently',
            'manipulative tendencies', 'unstable self', 'fear of rejection',
        ],
    },
    'Anxiety': {
        'strong': [
            'anxiety', 'anxious', 'panic attack', 'panicking', 'panic', 'phobia',
            'cant stop worrying', 'intrusive thoughts', 'ocd', 'obsessive',
            'heart racing', 'heart palpitation', 'hyperventilat', 'agoraphobia', 'social anxiety',
            'terrif', 'claustrophob', 'generalized anxiety', 'gad',
            'constant worry', 'catastrophizing', 'sense of doom', 'impending doom',
            'health anxiety', 'hypochondria', 'separation anxiety',
        ],
        'moderate': [
            'worried', 'nervous', 'fear', 'scared', 'uneasy', 'dread',
            'apprehensive', 'on edge', 'restless', 'overthinking', 'racing thoughts',
            'cant breathe', 'feel like i am dying', 'feel like im dying',
            'what if', 'worst case', 'spiraling', 'trembling', 'shaking', 'worry',
            'sweating', 'chest tightness', 'shortness of breath', 'cant relax',
            'avoidance', 'compulsive checking', 'butterflies', 'knot in my stomach',
        ],
    },
    'Depression': {
        'strong': [
            'depressed', 'depression', 'hopeless', 'worthless', 'empty inside',
            'nothing matters', 'no motivation', 'cant get out of bed',
            'lost interest', 'anhedonia', 'numb', 'cry all day',
            'major depression', 'clinical depression', 'persistent sadness',
            'cant feel anything', 'lost all interest', 'everything is gray',
            'whats the point', 'sleeping all day', 'self loathing',
            'complete despair',
        ],
        'moderate': [
            'so sad', 'feeling sad', 'lonely', 'miserable', 'unhappy', 'down',
            'gloomy', 'no energy', 'fatigued', 'withdrawn', 'isolated', 'tearful',
            'dont care anymore', 'feel like a burden', 'darkness',
            'appetite loss', 'cant sleep from sadness', 'hate myself',
            'everything feels heavy', 'cant enjoy anything', 'going through motions',
            'emotionally numb', 'void', 'bleak', 'despair',
        ],
    },
    'Suicidal': {
        'strong': [
            'suicid', 'kill myself', 'end my life', 'want to die',
            'no reason to live', 'better off dead', 'planning to end',
            'dont want to be alive', 'self harm', 'cutting myself',
            'overdose', 'jump off', 'hang myself',
            'wish i was never born', 'never been born',
            'take my own life', 'suicide note', 'final goodbye',
            'deserve to die', 'not worth living', 'rather be dead',
        ],
        'moderate': [
            'dont want to live', 'no point in living', 'wish i was dead',
            'cant go on', 'life isnt worth', 'disappear forever',
            'give up on life', 'no way out', 'ending it all',
            'no one would miss me', 'nobody would care',
            'world without me', 'saying goodbye to everyone',
            'giving away my things', 'no one cares if i die',
            'tired of living', 'cant take this life',
        ],
    },
    'Normal': {
        'strong': [
            'i am happy', 'feeling happy', 'so happy', 'very happy', 'great',
            'wonderful', 'fantastic', 'amazing day', 'feeling good', 'blessed',
            'grateful', 'content', 'joyful', 'im doing well', 'life is good',
            'love my life', 'having a good time', 'loving life',
            'everything is going well', 'feeling at peace', 'thriving',
            'in a good place', 'couldnt be better', 'on top of the world',
        ],
        'moderate': [
            'fine', 'okay', 'alright', 'not bad', 'pretty good', 'enjoying',
            'relaxed', 'calm', 'peaceful', 'optimistic', 'happy',
            'no complaints', 'making progress', 'accomplished', 'proud of myself',
            'looking forward to', 'excited about', 'hopeful',
            'things are looking up', 'getting better', 'on the right track',
            'productive', 'fulfilled',
        ],
    },
}

# HELPER FUNCTIONS

def _keyword_has_builtin_negation(keyword):
    """Return True if the keyword phrase already contains a negation word.
    e.g. 'cant stop worrying', 'no motivation', 'nothing matters'
    These should NOT be further negation-checked."""
    kw_words = set(keyword.lower().split())
    return bool(kw_words & NEGATION_SINGLES)


def _is_negated_in_context(keyword, text_lower, words):
    """Return True if a nearby negation word genuinely negates this keyword.
    Handles double-negatives like 'can't STOP worrying' → not negated.
    Respects sentence boundaries (commas, periods) that block negation."""
    try:
        kw_pos = text_lower.index(keyword)
    except ValueError:
        return False

    kw_word_pos = text_lower[:kw_pos].count(' ')
    start = max(0, kw_word_pos - NEGATION_WINDOW)
    context_words = words[start:kw_word_pos]

    # Check single-word negations in the window before the keyword
    for i, word in enumerate(context_words):
        clean = re.sub(r"[''`.,;:!?\"\(\)]", '', word)
        if clean in NEGATION_SINGLES:
            between_raw = context_words[i + 1:]
            # Sentence boundary (comma, period, etc.) blocks negation propagation
            if any(re.search(r'[.,;:!?]', w) for w in between_raw):
                continue
            # If a REINFORCEMENT word sits between the negation and the keyword,
            # it is a double-negative (reinforcing), NOT a true negation.
            between_clean = [re.sub(r"[''`.,;:!?\"\(\)]", '', w) for w in between_raw]
            if not any(w in REINFORCEMENT_WORDS for w in between_clean):
                return True

    # Check multi-word negation phrases
    context_str = ' '.join(re.sub(r"[''`]", '', w) for w in words[max(0, kw_word_pos - NEGATION_WINDOW - 1):kw_word_pos])
    # Block phrase negation across sentence boundaries too
    for phrase in NEGATION_PHRASES:
        if phrase in context_str:
            after_phrase = context_str[context_str.index(phrase) + len(phrase):]
            if not re.search(r'[.,;:!?]', after_phrase):
                return True

    return False


def preprocess_text(text):
    """
    Clean and normalize input text before sending to the model.
    Handles metaphorical / idiomatic language that misleads predictions.
    """
    cleaned = ' '.join(text.split())
    for pattern, replacement in METAPHOR_REPLACEMENTS:
        cleaned = pattern.sub(replacement, cleaned)
    return cleaned


def analyze_text_signals(text, preprocessed_text=None):
 
    text_for_scoring = (preprocessed_text or text).lower()
    stripped = re.sub(r"[''`]", '', text_for_scoring)
    words = text_for_scoring.split()
    original_words = text.split()

    #  Emphasis: ALL-CAPS words & exclamation marks increase intensity 
    caps_count = sum(1 for w in original_words if len(w) > 2 and w.isupper())
    excl_count = text.count('!')
    emphasis = 1.0 + min(0.3, caps_count * 0.08 + excl_count * 0.05)

    #  Contrast: find the LAST contrast conjunction position 
    contrast_pos = -1
    for i, w in enumerate(words):
        if w.rstrip('.,;:!?') in CONTRAST_WORDS:
            contrast_pos = i

    #  Score each class 
    scores = {}
    negated_classes = set()

    for cls, kw_dict in KEYWORD_SIGNALS.items():
        positive = 0.0
        negated_total = 0.0

        for tier, weight in [('strong', 1.0), ('moderate', 0.4)]:
            for keyword in kw_dict.get(tier, []):
                if keyword not in text_for_scoring and keyword not in stripped:
                    continue

                # Negation check (skip for keywords that contain built-in negation)
                if not _keyword_has_builtin_negation(keyword) and \
                   _is_negated_in_context(keyword, text_for_scoring, words):
                    negated_total += weight
                    continue

                # Contrast handling: after "but/however" → 1.25×, before → 0.75×
                pos_mult = 1.0
                if contrast_pos >= 0:
                    try:
                        kw_char_pos = text_for_scoring.index(keyword)
                        kw_word_pos = text_for_scoring[:kw_char_pos].count(' ')
                        if kw_word_pos > contrast_pos:
                            pos_mult = 1.25
                        elif kw_word_pos < contrast_pos:
                            pos_mult = 0.75
                    except ValueError:
                        pass

                positive += weight * pos_mult

        scores[cls] = positive * emphasis
        if negated_total > 0:
            negated_classes.add(cls)

    # Negation redirect: negating a clinical class implies the person is closer to Normal
    for neg_cls in negated_classes:
        if neg_cls != 'Normal':
            scores['Normal'] = scores.get('Normal', 0) + 0.6

    return scores, negated_classes, emphasis


def adjust_probabilities(probs_np, classes, text, preprocessed_text=None):
    """
     post-processing of model probabilities using text signals.
    """
    keyword_scores, negated_classes, emphasis = analyze_text_signals(text, preprocessed_text)

    total_signal = sum(keyword_scores.values())
    if total_signal < 0.4:
        return probs_np

    adjusted = probs_np.copy().astype(float)
    cls_index = {str(c): i for i, c in enumerate(classes)}

    # Best keyword class 
    best_kw_class = max(keyword_scores, key=keyword_scores.get)
    best_kw_score = keyword_scores[best_kw_class]
    best_kw_idx = cls_index.get(best_kw_class, -1)

    #  Suppress negated classes (very aggressive: model never handles negation) 
    for neg_cls in negated_classes:
        idx = cls_index.get(neg_cls, -1)
        if idx >= 0:
            adjusted[idx] *= 0.05

    # Post-negation dampening: when keywords redirect to Normal, suppress ALL
    # non-Normal classes so a non-negated class doesn't accidentally take over.
    normal_idx = cls_index.get('Normal', -1)
    if negated_classes and best_kw_class == 'Normal' and best_kw_score >= 0.6 and normal_idx >= 0:
        for j in range(len(adjusted)):
            if j != normal_idx:
                adjusted[j] *= 0.20

    model_top_idx = int(np.argmax(adjusted))
    model_top_cls = str(classes[model_top_idx])

    #  PHASE 1: Dampen competitors (general, config-driven) 
    if best_kw_idx >= 0 and best_kw_score >= 0.8 and best_kw_idx != model_top_idx:
        model_prob_kw = adjusted[best_kw_idx]
        gap = adjusted[model_top_idx] - model_prob_kw
        cfg = CLASS_CONFIG.get(best_kw_class, {})
        weakness = cfg.get('weakness', 0.0)
        safety = cfg.get('safety', 0)
        confused_with = cfg.get('confused_with', [])

        should_dampen = False
        if weakness >= 0.5 and gap > 0.10:
            should_dampen = True
        elif safety >= 5 and model_prob_kw < 0.30:
            should_dampen = True
        elif model_top_cls in confused_with and model_prob_kw < 0.15:
            should_dampen = True
        elif model_top_cls in confused_with and best_kw_score >= 2.0:
            should_dampen = True
        elif best_kw_score >= 1.5 and model_prob_kw < 0.15:
            should_dampen = True
        elif gap > 0.35 and best_kw_score >= 1.0:
            should_dampen = True

        if should_dampen:
            dampen = min(0.55, 0.20 + 0.12 * best_kw_score + weakness * 0.15)
            if model_top_cls in confused_with:
                dampen = max(dampen, 0.45)
            for j in range(len(adjusted)):
                if j != best_kw_idx:
                    adjusted[j] *= (1.0 - dampen)

    #  PHASE 2: Boost (general, config-driven) 
    for i, cls_val in enumerate(classes):
        cls_name = str(cls_val)
        signal = keyword_scores.get(cls_name, 0.0)
        if signal <= 0:
            continue

        cfg = CLASS_CONFIG.get(cls_name, {})
        weakness = cfg.get('weakness', 0.0)
        safety = cfg.get('safety', 0)
        confused_with = cfg.get('confused_with', [])
        signal_ratio = signal / max(total_signal, 1.0)
        prob = adjusted[i]

        if weakness >= 0.5 and signal >= 0.8:
            # Tiered boost for weak classes based on current model probability
            if prob < 0.01:
                base_boost = 0.60
            elif prob < 0.05:
                base_boost = 0.45
            elif prob < 0.20:
                base_boost = 0.35
            else:
                base_boost = 0.15
            boost = base_boost * signal_ratio * (1.0 + weakness * 0.3)
        elif safety >= 5 and signal >= 1.0:
            # Safety-critical: always significant boost
            boost = max(0.20, 0.40 * signal_ratio)
        elif signal >= 2.0 and prob < 0.20:
            # Very strong keyword evidence but model gives very low probability
            if prob < 0.01:
                base_boost = 0.60
            elif prob < 0.10:
                base_boost = 0.50
            else:
                base_boost = 0.40
            boost = base_boost * signal_ratio
        elif signal >= 1.0 and model_top_cls in confused_with:
            # Model is confused with a known pair → larger correction
            boost = 0.30 * signal_ratio
        elif signal >= 1.5:
            # Any class with strong keyword evidence
            boost = 0.25 * signal_ratio
        else:
            # Standard proportional adjustment
            top_prob = float(np.max(probs_np))
            keyword_weight = max(0.05, min(0.20, 0.30 - top_prob * 0.30))
            boost = keyword_weight * signal_ratio

        adjusted[i] += boost

    # Re-normalize to sum to 1
    adjusted = adjusted / adjusted.sum()
    return adjusted

# EXISTING ENDPOINTS

@app.route('/')
def home():
    """API info endpoint"""
    return jsonify({
        'service': 'MindCore Mental Health API',
        'version': '2.1.0',
        'model': 'BERT + Gemini',
        'status': 'running',
        'model_loaded': model is not None,
        'ai_report_enabled': hf_api_token is not None,
        'device': str(device) if device else 'Not initialized',
        'endpoints': {
            '/': 'API info',
            '/health': 'Health check',
            '/predict': 'POST - Mental health prediction',
            '/classes': 'GET - Available classes',
            '/generate-report': 'POST - Generate consoling mood report'
        }
    })


@app.route('/health')
def health():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'model_loaded': model is not None,
        'tokenizer_loaded': tokenizer is not None,
        'classes_loaded': classes is not None,
        'ai_report_enabled': hf_api_token is not None,
        'device': str(device) if device else 'Not initialized',
        'timestamp': datetime.now().isoformat()
    })


@app.route('/classes')
def get_classes():
    """ available prediction classes"""
    if classes is None:
        return jsonify({'error': 'Classes not loaded'}), 503
    
    return jsonify({
        'classes': classes.tolist() if isinstance(classes, np.ndarray) else list(classes),
        'count': len(classes)
    })


@app.route('/predict', methods=['POST'])
def predict():
    """
    Main prediction endpoint
    
    Request: {"text": "I feel anxious and stressed"}
    Response: {
        "success": true,
        "prediction": "Anxiety",
        "confidence": 0.85,
        "severity": "Moderate",
        "color": "#FF9800"
    }
    """
    try:
        # Check if model is loaded
        if model is None or tokenizer is None or classes is None:
            return jsonify({
                'success': False,
                'error': 'Model not loaded'
            }), 503
        
        # Get request data
        data = request.get_json()
        
        if not data:
            return jsonify({
                'success': False,
                'error': 'No JSON data provided'
            }), 400
        
        text = data.get('text', '').strip()
        
        if not text:
            return jsonify({
                'success': False,
                'error': 'No text provided'
            }), 400
        
        if len(text) < 3:
            return jsonify({
                'success': False,
                'error': 'Text too short (minimum 3 characters)'
            }), 400
        
        logger.info(f"📝 Analyzing: '{text[:50]}...'")
        
        # Preprocess: clean metaphors and normalize text
        processed_text = preprocess_text(text)
        
        # Tokenize input
        inputs = tokenizer(
            processed_text,
            return_tensors="pt",
            truncation=True,
            padding=True,
            max_length=512
        )
        
        # Move to device
        inputs = {key: val.to(device) for key, val in inputs.items()}
        
        # Get prediction
        with torch.no_grad():
            outputs = model(**inputs)
            logits = outputs.logits
            probabilities = torch.softmax(logits, dim=1)
        
        # Get predicted class
        probs_np = probabilities[0].cpu().numpy()
        
        # Adjust probabilities using keyword signals (helps with Stress, Bipolar, Personality disorder)
        adjusted_probs = adjust_probabilities(probs_np, classes, text, preprocessed_text=processed_text)
        
        predicted_idx = int(np.argmax(adjusted_probs))
        confidence = float(adjusted_probs[predicted_idx])
        prediction = classes[predicted_idx]
        
        # Handle numpy string type
        if isinstance(prediction, np.str_):
            prediction = str(prediction)
        
        # Get top 3 predictions
        top_indices = np.argsort(adjusted_probs)[-3:][::-1]
        
        top_predictions = []
        for idx in top_indices:
            class_name = classes[idx]
            if isinstance(class_name, np.str_):
                class_name = str(class_name)
            
            top_predictions.append({
                'class': class_name,
                'probability': float(adjusted_probs[idx]),
                'severity': SEVERITY_MAP.get(class_name, 'Unknown'),
                'color': COLOR_MAP.get(class_name, '#757575')
            })
        
        # Build response
        response = {
            'success': True,
            'prediction': prediction,
            'confidence': round(confidence, 4),
            'severity': SEVERITY_MAP.get(prediction, 'Unknown'),
            'color': COLOR_MAP.get(prediction, '#757575'),
            'top_predictions': top_predictions,
            'timestamp': datetime.now().isoformat()
        }
        
        logger.info(f"✅ Prediction: {prediction} ({confidence:.2%})")
        
        return jsonify(response), 200
        
    except Exception as e:
        logger.error(f"❌ Prediction error: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500



#  GENERATE CONSOLING REPORT 

@app.route('/generate-report', methods=['POST'])
def generate_report():
    """Generate a consoling mood report using Hugging Face Inference API"""
    try:
        import requests as http_requests
        
        if hf_api_token is None:
            return jsonify({
                'success': False,
                'error': 'AI report not configured. Add HF_API_TOKEN to .env file.'
            }), 503

        data = request.get_json()
        if not data:
            return jsonify({'success': False, 'error': 'No JSON data provided'}), 400

        mood_history = data.get('mood_history', [])
        user_name = data.get('user_name', 'friend')

        if not mood_history:
            return jsonify({'success': False, 'error': 'No mood history provided'}), 400

        #  Build mood summary 
        mood_counts = {}
        for entry in mood_history:
            pred = entry.get('prediction', 'Unknown')
            mood_counts[pred] = mood_counts.get(pred, 0) + 1

        dominant_mood = max(mood_counts, key=mood_counts.get)
        dates = [entry.get('date', '') for entry in mood_history]

        clean_dates = []
        for d in dates:
            if d and len(d) >= 10:
                clean_dates.append(d[:10])
            else:
                clean_dates.append(d)

        period_start = min(clean_dates) if clean_dates else ''
        period_end = max(clean_dates) if clean_dates else ''

        #  Format mood history for prompt 
        history_text = ""
        for entry in mood_history:
            date_str = entry.get('date', 'N/A')
            if date_str and len(date_str) >= 10:
                date_str = date_str[:10]

            conf = entry.get('confidence', 0)
            conf_display = f"{conf:.0%}" if isinstance(conf, float) else str(conf)

            history_text += (
                f"- Date: {date_str}, "
                f"Mood: {entry.get('prediction', 'N/A')} "
                f"({conf_display}), "
                f"Severity: {entry.get('severity', 'N/A')}, "
                f"Said: \"{entry.get('input_text', 'N/A')}\"\n"
            )

        #  Build the prompt 
        prompt = f"""You are MindCore Companion, a warm caring friend in a mental health app. 
Write a personal consoling mood report for {user_name}.

Their mood data from the past {len(mood_history)} days:
{history_text}

Dominant mood: {dominant_mood}
Mood breakdown: {json.dumps(mood_counts)}

Rules:
1. Greet them by name warmly
2. Reference their actual words and moods day by day
3. Be encouraging and provide gentle perspective
4. If Suicidal appears, take it seriously and include crisis helpline: Pakistan 0311-7786264.
5. End with hope and encouragement
6. Tone: warm, personal, like a best friend texting
7. Length: 200-350 words

Write the report now:"""

        logger.info(f"🤖 Generating consoling report for {user_name} ({len(mood_history)} entries)...")

        import time

        models_to_try = [
            "Qwen/Qwen2.5-Coder-32B-Instruct",
            "deepseek-ai/DeepSeek-R1",
            "Qwen/Qwen2.5-7B-Instruct-1M",
            "moonshotai/Kimi-K2-Instruct-0905",
        ]

        headers = {
            "Authorization": f"Bearer {hf_api_token}",
            "Content-Type": "application/json"
        }

        api_url = "https://router.huggingface.co/v1/chat/completions"

        report_text = None
        last_error = None

        for model_name in models_to_try:
            try:
                logger.info(f"🔄 Trying model: {model_name}")

                payload = {
                    "model": model_name,
                    "messages": [
                        {"role": "user", "content": prompt}
                    ],
                    "max_tokens": 512,
                    "temperature": 0.7,
                    "top_p": 0.9
                }

                resp = http_requests.post(api_url, headers=headers, json=payload, timeout=60)

                if resp.status_code == 200:
                    result = resp.json()

                    if 'choices' in result and len(result['choices']) > 0:
                        report_text = result['choices'][0]['message']['content'].strip()

                    if report_text and len(report_text) > 50:
                        logger.info(f"✅ Success with model: {model_name}")
                        break
                    else:
                        logger.warning(f"⚠️ Model {model_name} returned short/empty response")
                        report_text = None
                        continue

                elif resp.status_code == 503:
                    logger.info(f"⏳ Model {model_name} is loading, waiting 20s...")
                    time.sleep(20)
                    resp = http_requests.post(api_url, headers=headers, json=payload, timeout=90)
                    if resp.status_code == 200:
                        result = resp.json()
                        if 'choices' in result and len(result['choices']) > 0:
                            report_text = result['choices'][0]['message']['content'].strip()
                        if report_text and len(report_text) > 50:
                            logger.info(f"✅ Success with model: {model_name} (after loading)")
                            break
                    continue

                else:
                    last_error = f"{model_name}: HTTP {resp.status_code} - {resp.text[:200]}"
                    logger.warning(f"⚠️ {last_error}")
                    continue

            except Exception as model_error:
                last_error = str(model_error)
                logger.warning(f"⚠️ Model {model_name} error: {last_error[:150]}")
                continue

        if report_text is None or len(report_text) < 50:
            error_msg = last_error or "All models failed to generate report"
            logger.error(f"❌ {error_msg}")
            return jsonify({
                'success': False,
                'error': f'Report generation failed: {error_msg}'
            }), 500

        logger.info(f"✅ Report generated ({len(report_text)} chars)")

        return jsonify({
            'success': True,
            'report': report_text,
            'summary': {
                'total_entries': len(mood_history),
                'dominant_mood': dominant_mood,
                'mood_counts': mood_counts,
                'period': f"{period_start} to {period_end}"
            },
            'generated_at': datetime.now().isoformat()
        }), 200

    except Exception as e:
        logger.error(f"❌ Report generation error: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'success': False, 'error': str(e)}), 500
  
# Error Handlers

@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint not found'}), 404


@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Internal server error'}), 500

# Start Server


if __name__ == '__main__':
    logger.info("=" * 50)
    logger.info("🧠 MindCore Mental Health API")
    logger.info("=" * 50)
    
    if load_model():

        load_gemini()
        
        logger.info("🚀 Starting server on http://localhost:5000")
        debug_mode = os.getenv('FLASK_DEBUG', 'false').strip().lower() == 'true'
        app.run(host='0.0.0.0', port=5000, debug=debug_mode)
    else:
        logger.error("❌ Failed to load model. Server not started.")