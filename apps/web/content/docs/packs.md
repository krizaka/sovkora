# Packs & Studios

A **Studio** is a business workflow — a versioned DAG of steps (model calls, transforms, human approvals,
connectors) with its own input form. A **pack** is a directory that ships Studios: a `pack.yaml` manifest,
its translations, the blueprints and, for a `WORKER` pack, its own worker. Installing a pack writes data —
capability rows, a price, catalogue entries — and changes no code.

> **Packs are not use-cases.** A pack is a business offer — Studios a customer installs and runs. A
> use-case is a technical entry point of the engine (`chat.assistant`, `studio.run`…) declared
> in Java in `orazaka-business` ([business layer](BUSINESS.md)); every Studio run goes through the `studio.run` one.

## The packs

```pack-index
[
 {
  "key": "realestate-studio",
  "title": "Real-Estate Studio",
  "tagline": "Your listings become Reels ready to publish.",
  "tier": "DATA",
  "category": "Creativity & media",
  "studios": 1
 },
 {
  "key": "orazaka-media",
  "title": "Media Studios",
  "tagline": "Generate, listen, describe — without leaving a run.",
  "tier": "DATA",
  "category": "Creativity & media",
  "studios": 16
 },
 {
  "key": "outbound-prospection",
  "title": "Outbound Prospecting",
  "tagline": "From a prospect list to a sequence that ships.",
  "tier": "DATA",
  "category": "Business & documents",
  "studios": 3
 },
 {
  "key": "data-analytics",
  "title": "Recommendations & insights",
  "tagline": "A feed per person and analytics per creator, on your own data.",
  "tier": "DATA",
  "category": "Data & analytics",
  "studios": 3
 },
 {
  "key": "bien-etre",
  "title": "Wellbeing",
  "tagline": "Put into words what you live through, and bring it to your sessions.",
  "tier": "DATA",
  "category": "Wellbeing",
  "studios": 4
 },
 {
  "key": "document-validation",
  "title": "Document Verification",
  "tagline": "Check a contract against the rules, and a PDF for signs of tampering.",
  "tier": "WORKER",
  "category": "Business & documents",
  "studios": 2
 },
 {
  "key": "media-safety",
  "title": "Media safety",
  "tagline": "Is this photo, video or voice message safe to show?",
  "tier": "WORKER",
  "category": null,
  "studios": 1
 },
 {
  "key": "message-authenticity",
  "title": "Message authenticity",
  "tagline": "Does this message read like a person wrote it? A signal, never a verdict.",
  "tier": "WORKER",
  "category": null,
  "studios": 1
 },
 {
  "key": "profile-photo-check",
  "title": "Profile photo check",
  "tagline": "Are these photos one real adult's profile?",
  "tier": "WORKER",
  "category": null,
  "studios": 1
 },
 {
  "key": "trade-showcase",
  "title": "Trade Showcase",
  "tagline": null,
  "tier": "DATA",
  "category": null,
  "studios": 1
 }
]
```

### Real-Estate Studio

```pack
{
 "key": "realestate-studio",
 "title": "Real-Estate Studio",
 "tagline": "Your listings become Reels ready to publish.",
 "description": "The real-estate agent's pack: the Real-Estate Reels Studio and the credits to run it.",
 "version": "1.3.0",
 "tier": "DATA",
 "kind": null,
 "distribution": "OSS",
 "regulatoryClass": "STANDARD",
 "category": "Creativity & media",
 "listed": true,
 "workers": [],
 "controls": {
  "scopeGuard": false,
  "consent": false,
  "safety": false
 },
 "rulesets": [],
 "studios": [
  {
   "key": "realestate-reels",
   "label": "Real-Estate Reels",
   "summary": "Five photos become a Reel ready to publish.",
   "description": "Property photos, a three-line brief, and you leave with a 9:16 Reel, its caption and its hashtags.",
   "inputs": [
    {
     "key": "photos",
     "title": "Photos du bien"
    },
    {
     "key": "clip",
     "title": "Clip vidéo (optionnel)"
    },
    {
     "key": "propertyBrief",
     "title": "Le bien en 3 lignes"
    },
    {
     "key": "platform",
     "title": "Plateforme"
    }
   ],
   "steps": 4,
   "stepKinds": [
    "CAPABILITY",
    "APPROVAL"
   ],
   "credits": 2800
  }
 ]
}
```

### Media Studios

```pack
{
 "key": "orazaka-media",
 "title": "Media Studios",
 "tagline": "Generate, listen, describe — without leaving a run.",
 "description": null,
 "version": "1.13.0",
 "tier": "DATA",
 "kind": "TOOLKIT",
 "distribution": "OSS",
 "regulatoryClass": "STANDARD",
 "category": "Creativity & media",
 "listed": true,
 "workers": [],
 "controls": {
  "scopeGuard": false,
  "consent": false,
  "safety": false
 },
 "rulesets": [],
 "studios": [
  {
   "key": "image-generation",
   "label": "Image generation",
   "summary": "One sentence, one image.",
   "description": null,
   "inputs": [
    {
     "key": "prompt",
     "title": "Ce que vous voulez voir"
    },
    {
     "key": "size",
     "title": "Format"
    },
    {
     "key": "negativePrompt",
     "title": "À éviter (facultatif)"
    },
    {
     "key": "seed",
     "title": "Graine (facultatif)"
    },
    {
     "key": "model",
     "title": "Modèle"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 200
  },
  {
   "key": "art-golden-hour",
   "label": "Golden-hour editorial",
   "summary": "Your subject, in warm low light, shot like a magazine spread.",
   "description": "Lifestyle photography at golden hour: raking sunlight, an 85 mm lens wide open, weathered wood, wool and brass, an amber and terracotta palette. Describe who and where — the light, the optics, the materials and the palette are taken care of.",
   "inputs": [
    {
     "key": "subject",
     "title": "Votre sujet"
    },
    {
     "key": "size",
     "title": "Format"
    },
    {
     "key": "seed",
     "title": "Graine (facultatif)"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 200
  },
  {
   "key": "art-miniature",
   "label": "Tilt-shift miniature",
   "summary": "Your scene as a handcrafted diorama, shot in macro.",
   "description": "A 1:24 diorama of figurines and props in felt, embroidered thread and painted resin, a razor-thin plane of focus and soft window light. Describe the scene; it becomes a miniature.",
   "inputs": [
    {
     "key": "subject",
     "title": "Votre sujet"
    },
    {
     "key": "size",
     "title": "Format"
    },
    {
     "key": "seed",
     "title": "Graine (facultatif)"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 200
  },
  {
   "key": "art-still-life",
   "label": "Artisanal still life",
   "summary": "Objects and produce in single-window light.",
   "description": "Chiaroscuro on a weathered table: hand-thrown ceramics, hammered copper, linen and fresh produce, a muted earthy palette. Name the objects; they are arranged and lit.",
   "inputs": [
    {
     "key": "subject",
     "title": "Votre sujet"
    },
    {
     "key": "size",
     "title": "Format"
    },
    {
     "key": "seed",
     "title": "Graine (facultatif)"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 200
  },
  {
   "key": "art-montreal-night",
   "label": "Montreal neon night",
   "summary": "Wet streets, brick and spiral staircases under magenta and teal.",
   "description": "Montreal after rain: cobblestones mirroring window light and neon, light mist, a 35 mm lens wide open, cinematic grading. Describe the place and the moment.",
   "inputs": [
    {
     "key": "subject",
     "title": "Votre sujet"
    },
    {
     "key": "size",
     "title": "Format"
    },
    {
     "key": "seed",
     "title": "Graine (facultatif)"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 200
  },
  {
   "key": "video-generation",
   "label": "Video generation",
   "summary": "A vertical short (9:16) or a wide shot, described then rendered.",
   "description": null,
   "inputs": [
    {
     "key": "prompt",
     "title": "Ce qui doit se passer à l'écran"
    },
    {
     "key": "durationSeconds",
     "title": "Durée (secondes)"
    },
    {
     "key": "aspect",
     "title": "Format"
    },
    {
     "key": "image",
     "title": "Image d'amorce (facultatif)"
    },
    {
     "key": "model",
     "title": "Modèle"
    },
    {
     "key": "seed",
     "title": "Graine"
    },
    {
     "key": "upscale",
     "title": "Netteté"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 3600
  },
  {
   "key": "cinematic-treatment",
   "label": "Cinematic short — treatment",
   "summary": "A short brief becomes a story: an arc, intentions, living shots, directed lines, music, sound.",
   "description": null,
   "inputs": [
    {
     "key": "brief",
     "title": "Le brief"
    },
    {
     "key": "aspect",
     "title": "Format d'image"
    },
    {
     "key": "durationSeconds",
     "title": "Durée (secondes)"
    },
    {
     "key": "notes",
     "title": "Notes de la critique"
    },
    {
     "key": "language",
     "title": "Langue des textes"
    },
    {
     "key": "model",
     "title": "Modèle de langage"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 30
  },
  {
   "key": "cinematic-critique",
   "label": "Cinematic short — critique",
   "summary": "A director's read of a treatment: scores, flat or off-topic shots rejected, what to change.",
   "description": null,
   "inputs": [
    {
     "key": "treatment",
     "title": "Le traitement à juger"
    },
    {
     "key": "model",
     "title": "Modèle de langage"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 10
  },
  {
   "key": "cinematic-script",
   "label": "Cinematic short — script",
   "summary": "A brief becomes a shot list: 8–12 shots, original characters, music, voice-over.",
   "description": null,
   "inputs": [
    {
     "key": "brief",
     "title": "Le brief"
    },
    {
     "key": "format",
     "title": "Le format"
    },
    {
     "key": "aspect",
     "title": "Format d'image"
    },
    {
     "key": "durationSeconds",
     "title": "Durée (secondes)"
    },
    {
     "key": "shots",
     "title": "Nombre de plans"
    },
    {
     "key": "language",
     "title": "Langue des textes"
    },
    {
     "key": "model",
     "title": "Modèle de langage"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 40
  },
  {
   "key": "cinematic-cut",
   "label": "Cinematic short — cut",
   "summary": "Your shots edited into one film: cuts, ramps, grade, titles, the AI label.",
   "description": null,
   "inputs": [
    {
     "key": "clips",
     "title": "Les plans"
    },
    {
     "key": "edit",
     "title": "La liste de décisions de montage (JSON)"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 900
  },
  {
   "key": "video-with-sound",
   "label": "Video with sound",
   "summary": "A video, then its sound: a music bed and optional foley, mixed at -14 LUFS.",
   "description": null,
   "inputs": [
    {
     "key": "prompt",
     "title": "Ce qui doit se passer à l'écran"
    },
    {
     "key": "durationSeconds",
     "title": "Durée (secondes)"
    },
    {
     "key": "aspect",
     "title": "Format"
    },
    {
     "key": "image",
     "title": "Image d'amorce (facultatif)"
    },
    {
     "key": "model",
     "title": "Modèle"
    },
    {
     "key": "seed",
     "title": "Graine (facultatif)"
    },
    {
     "key": "music",
     "title": "La musique"
    },
    {
     "key": "foley",
     "title": "Les bruitages (facultatif)"
    },
    {
     "key": "foleyModel",
     "title": "Modèle de bruitage (facultatif)"
    }
   ],
   "steps": 2,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 4500
  },
  {
   "key": "video-sound",
   "label": "Sound for a video",
   "summary": "Give any clip sound: music, foley, a voice-over. The silent original is kept.",
   "description": null,
   "inputs": [
    {
     "key": "video",
     "title": "La vidéo"
    },
    {
     "key": "music",
     "title": "La musique"
    },
    {
     "key": "foley",
     "title": "Les bruitages (facultatif)"
    },
    {
     "key": "foleyModel",
     "title": "Modèle de bruitage (facultatif)"
    },
    {
     "key": "seed",
     "title": "Graine (facultatif)"
    },
    {
     "key": "voice",
     "title": "Voix off (facultatif)"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 900
  },
  {
   "key": "speech-synthesis",
   "label": "Speech synthesis",
   "summary": "Your text, read aloud.",
   "description": null,
   "inputs": [
    {
     "key": "text",
     "title": "Le texte à lire"
    },
    {
     "key": "model",
     "title": "Voix"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 40
  },
  {
   "key": "image-analysis",
   "label": "Image analysis",
   "summary": "What an image shows, in words.",
   "description": null,
   "inputs": [
    {
     "key": "assetId",
     "title": "Votre image"
    },
    {
     "key": "prompt",
     "title": "Ce que vous voulez savoir"
    },
    {
     "key": "model",
     "title": "Modèle"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 30
  },
  {
   "key": "audio-analysis",
   "label": "Audio transcription",
   "summary": "What was said, written down.",
   "description": null,
   "inputs": [
    {
     "key": "assetId",
     "title": "Votre fichier audio"
    },
    {
     "key": "model",
     "title": "Modèle"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 60
  },
  {
   "key": "video-analysis",
   "label": "Video analysis",
   "summary": "A video's speech, and its keyframes.",
   "description": null,
   "inputs": [
    {
     "key": "assetId",
     "title": "Votre vidéo"
    },
    {
     "key": "model",
     "title": "Modèle"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 90
  }
 ]
}
```

### Outbound Prospecting

```pack
{
 "key": "outbound-prospection",
 "title": "Outbound Prospecting",
 "tagline": "From a prospect list to a sequence that ships.",
 "description": "The salesperson's pack: qualify a list, write each prospect's angle, and get the follow-ups out — three Studios over the same list.",
 "version": "1.3.0",
 "tier": "DATA",
 "kind": null,
 "distribution": "OSS",
 "regulatoryClass": "STANDARD",
 "category": "Business & documents",
 "listed": true,
 "workers": [],
 "controls": {
  "scopeGuard": false,
  "consent": false,
  "safety": false
 },
 "rulesets": [],
 "studios": [
  {
   "key": "outbound-prospection",
   "label": "Outbound Prospecting",
   "summary": "Your prospect list becomes a sequence ready to send.",
   "description": "Describe your target and your offer: Orazaka writes a per-prospect angle, then a sequence ready to leave for your channel.",
   "inputs": [
    {
     "key": "prospects",
     "title": "Vos prospects"
    },
    {
     "key": "offer",
     "title": "Votre offre en une phrase"
    },
    {
     "key": "icp",
     "title": "Votre client idéal"
    }
   ],
   "steps": 4,
   "stepKinds": [
    "CAPABILITY",
    "TRANSFORM",
    "APPROVAL",
    "CONNECTOR"
   ],
   "credits": 250
  },
  {
   "key": "lead-research",
   "label": "Lead Research",
   "summary": "Your raw list becomes a prioritised dossier.",
   "description": "One brief per prospect — sector, buying trigger, expected objection — then a ranking so you know who to call first.",
   "inputs": [
    {
     "key": "prospects",
     "title": "Vos prospects"
    },
    {
     "key": "offer",
     "title": "Votre offre en une phrase"
    },
    {
     "key": "icp",
     "title": "Votre client idéal"
    }
   ],
   "steps": 3,
   "stepKinds": [
    "CAPABILITY",
    "TRANSFORM"
   ],
   "credits": 380
  },
  {
   "key": "followup-sequences",
   "label": "Follow-up Sequences",
   "summary": "Your first contacts become follow-ups that go out.",
   "description": "A multi-touch follow-up per prospect, unified into one voice, approved by you, then posted to your channel.",
   "inputs": [
    {
     "key": "prospects",
     "title": "Prospects à relancer"
    },
    {
     "key": "offer",
     "title": "Votre offre en une phrase"
    },
    {
     "key": "context",
     "title": "Ce qui s'est passé au premier contact"
    }
   ],
   "steps": 5,
   "stepKinds": [
    "CAPABILITY",
    "TRANSFORM",
    "APPROVAL",
    "CONNECTOR"
   ],
   "credits": 510
  }
 ]
}
```

### Recommendations & insights

```pack
{
 "key": "data-analytics",
 "title": "Recommendations & insights",
 "tagline": "A feed per person and analytics per creator, on your own data.",
 "description": "The recommender your product calls with a service key: send pseudonymous events and your eligible items, read a \"for you\" feed, similar items and creator insights — every item says why it is there. Two Studios read your videos and suggest titles and tags.",
 "version": "1.6.0",
 "tier": "DATA",
 "kind": null,
 "distribution": "OSS",
 "regulatoryClass": "STANDARD",
 "category": "Data & analytics",
 "listed": true,
 "workers": [],
 "controls": {
  "scopeGuard": false,
  "consent": false,
  "safety": false
 },
 "rulesets": [],
 "studios": [
  {
   "key": "data-driven-ui",
   "label": "Data-driven UI",
   "summary": "A CSV and a question become a live dashboard.",
   "description": "Give your data (a CSV, a JSON export of your analytics) and ask: the answer comes as figures, charts and a table you can read at a glance. Every number comes from your data.",
   "inputs": [
    {
     "key": "data",
     "title": "Vos données (facultatif)"
    },
    {
     "key": "question",
     "title": "Votre question"
    },
    {
     "key": "language",
     "title": "Langue"
    },
    {
     "key": "model",
     "title": "Modèle de langage"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 30
  },
  {
   "key": "video-understanding",
   "label": "Video understanding",
   "summary": "What a video is about — topics, tags and safety signals, from a keyframe.",
   "description": "A keyframe goes through the vision model, then a reading in JSON: a summary, topics, tags, the language and safety labels. The labels go to a human moderator, never to an automatic takedown.",
   "inputs": [
    {
     "key": "assetId",
     "title": "L'image clé"
    },
    {
     "key": "title",
     "title": "Le titre de la vidéo"
    },
    {
     "key": "description",
     "title": "La description (facultative)"
    },
    {
     "key": "language",
     "title": "Langue de la lecture"
    }
   ],
   "steps": 2,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 40
  },
  {
   "key": "creator-suggestions",
   "label": "Creator suggestions",
   "summary": "Three titles, a description and tags — proposals the creator approves.",
   "description": "From the creator's own words and the tags that perform with their audience. Nothing is invented, nothing is published without the creator.",
   "inputs": [
    {
     "key": "title",
     "title": "Votre titre"
    },
    {
     "key": "description",
     "title": "Votre description (facultative)"
    },
    {
     "key": "tags",
     "title": "Vos mots-clés (facultatif)"
    },
    {
     "key": "audienceTags",
     "title": "Ce qui marche auprès de votre audience (facultatif)"
    },
    {
     "key": "language",
     "title": "Langue"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 15
  }
 ]
}
```

### Wellbeing

```pack
{
 "key": "bien-etre",
 "title": "Wellbeing",
 "tagline": "Put into words what you live through, and bring it to your sessions.",
 "description": "Guided journaling, session preparation and mood tracking. Not care: no diagnosis, no advice on treatment, no emergency service — what you bring to a professional, not instead of one. Health data, kept 30 days at most, never used to train a model.",
 "version": "1.6.0",
 "tier": "DATA",
 "kind": null,
 "distribution": "CLOUD",
 "regulatoryClass": "REGULATED",
 "category": "Wellbeing",
 "listed": true,
 "workers": [],
 "controls": {
  "scopeGuard": true,
  "consent": true,
  "safety": true
 },
 "rulesets": [],
 "studios": [
  {
   "key": "guided-journaling",
   "label": "Guided Journaling",
   "summary": "Your words, put in order — in your own voice.",
   "description": "Write what you want to set down, as it comes: the Studio tidies it into a clean entry, in the first person, without adding advice or interpretation.",
   "inputs": [
    {
     "key": "entry",
     "title": "Ce que vous voulez mettre par écrit"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 40
  },
  {
   "key": "session-preparation",
   "label": "Session Preparation",
   "summary": "Your notes since the last session become the points to raise.",
   "description": "Paste what you noted, in any order: the Studio turns it into the list of points to bring to your next session, without interpreting anything.",
   "inputs": [
    {
     "key": "notes",
     "title": "Ce que vous avez noté depuis la dernière séance"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 40
  },
  {
   "key": "mood-tracking",
   "label": "Mood Tracking",
   "summary": "What your daily notes show, described — never concluded.",
   "description": "One line a day is enough: the Studio describes what the entries show over time, and draws no conclusion from them.",
   "inputs": [
    {
     "key": "entries",
     "title": "Vos relevés"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 40
  },
  {
   "key": "session-summary",
   "label": "Session Summary",
   "summary": "What you keep of a conversation — themes, what helped, one small step.",
   "description": "Hand over the turns of a conversation: the Studio returns what you keep of it — up to three themes, what helped, one small step, and what to bring to your therapist — described in your words, never explained.",
   "inputs": [
    {
     "key": "turns",
     "title": "Le moment"
    },
    {
     "key": "thread",
     "title": "Le fil"
    },
    {
     "key": "moodBefore",
     "title": "Humeur avant (auto-déclarée)"
    },
    {
     "key": "moodAfter",
     "title": "Humeur après (auto-déclarée)"
    },
    {
     "key": "locale",
     "title": "Langue du résumé"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 40
  }
 ]
}
```

### Document Verification

```pack
{
 "key": "document-validation",
 "title": "Document Verification",
 "tagline": "Check a contract against the rules, and a PDF for signs of tampering.",
 "description": "Check a document against a versioned body of rules — every finding cites its rule, its source and the passage that justifies it — and examine a PDF file for the signals that something was altered. Reports, never legal advice.",
 "version": "1.7.3",
 "tier": "WORKER",
 "kind": null,
 "distribution": "CLOUD",
 "regulatoryClass": "SENSITIVE",
 "category": "Business & documents",
 "listed": true,
 "workers": [
  "orazaka-worker-validation"
 ],
 "controls": {
  "scopeGuard": true,
  "consent": false,
  "safety": false
 },
 "rulesets": [
  {
   "label": "Bail de résidence principale — Région wallonne",
   "jurisdiction": "BE-WAL",
   "rules": 4,
   "versions": [
    "2025-01-01"
   ]
  },
  {
   "label": "Bail d'habitation — France",
   "jurisdiction": "FR",
   "rules": 10,
   "versions": [
    "2024-07-01",
    "2026-07-01"
   ]
  },
  {
   "label": "Facture — Québec (TPS et TVQ)",
   "jurisdiction": "QC",
   "rules": 8,
   "versions": [
    "2024-06-20"
   ]
  },
  {
   "label": "Bail de logement — Québec",
   "jurisdiction": "QC",
   "rules": 10,
   "versions": [
    "2024-02-21"
   ]
  },
  {
   "label": "Contrat de service — Québec (première étape)",
   "jurisdiction": "QC",
   "rules": 4,
   "versions": [
    "2026-10-19"
   ]
  }
 ],
 "studios": [
  {
   "key": "document-authenticity",
   "label": "Document Authenticity",
   "summary": "A PDF's metadata, structure and fonts, examined for signs of tampering.",
   "description": "Examines the file itself — who produced it, when, with what, and whether it was edited afterwards — and lists the points worth clearing up. It never judges what the document says.",
   "inputs": [
    {
     "key": "documentBase64",
     "title": "Le document"
    },
    {
     "key": "expectedIssuer",
     "title": "Émetteur attendu (facultatif)"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 30
  },
  {
   "key": "compliance-check",
   "label": "Compliance Check",
   "summary": "Your lease or contract, checked against the rules of its jurisdiction.",
   "description": "Runs a lease or a contract against the rules of its jurisdiction, as they stood on a date you pick. Each finding gives its rule, its source and the passage of the document.",
   "inputs": [
    {
     "key": "document",
     "title": "Document à contrôler"
    },
    {
     "key": "jurisdiction",
     "title": "Juridiction"
    },
    {
     "key": "asOf",
     "title": "Règles en vigueur à la date du"
    },
    {
     "key": "ruleset",
     "title": "Corpus nommé"
    }
   ],
   "steps": 3,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 120
  }
 ]
}
```

### Media safety

```pack
{
 "key": "media-safety",
 "title": "Media safety",
 "tagline": "Is this photo, video or voice message safe to show?",
 "description": "Judges pictures (a photo, or frames of a video) and a voice message's transcript against a short, public set of lines: no nudity or sexual content, no graphic violence, no threats or harassment. An answer nobody could read is never a pass.",
 "version": "1.2.0",
 "tier": "WORKER",
 "kind": null,
 "distribution": "CLOUD",
 "regulatoryClass": "SENSITIVE",
 "category": null,
 "listed": false,
 "workers": [
  "orazaka-worker-safety"
 ],
 "controls": {
  "scopeGuard": true,
  "consent": false,
  "safety": false
 },
 "rulesets": [],
 "studios": [
  {
   "key": "media-safety",
   "label": "Media safety",
   "summary": "A safety check that fails closed.",
   "description": "A vision question per picture, a transcript and a text judgement for sound, then closed rules. Unreadable means held, never approved.",
   "inputs": [
    {
     "key": "frames",
     "title": "Les images à examiner"
    },
    {
     "key": "frameCount",
     "title": "Le nombre d'images"
    },
    {
     "key": "audio",
     "title": "Le son à juger (facultatif)"
    },
    {
     "key": "expectsSpeech",
     "title": "Il y a du son à juger"
    }
   ],
   "steps": 4,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 30
  }
 ]
}
```

### Message authenticity

```pack
{
 "key": "message-authenticity",
 "title": "Message authenticity",
 "tagline": "Does this message read like a person wrote it? A signal, never a verdict.",
 "description": "Reads one message with a small open classifier and answers a coarse level — never a score. It can be wrong, above all on short texts and on non-native or French writing. It never writes a message for anyone.",
 "version": "1.3.0",
 "tier": "WORKER",
 "kind": null,
 "distribution": "CLOUD",
 "regulatoryClass": "SENSITIVE",
 "category": null,
 "listed": false,
 "workers": [
  "orazaka-worker-authenticity"
 ],
 "controls": {
  "scopeGuard": true,
  "consent": false,
  "safety": false
 },
 "rulesets": [],
 "studios": [
  {
   "key": "message-authenticity",
   "label": "Message authenticity",
   "summary": "A coarse signal on a text, never a verdict.",
   "description": "One step, no generative model. The text is read and forgotten; the answer is low, mid, high or unavailable.",
   "inputs": [
    {
     "key": "text",
     "title": "Le message"
    },
    {
     "key": "locale",
     "title": "Langue du compte (facultatif)"
    }
   ],
   "steps": 1,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 5
  }
 ]
}
```

### Profile photo check

```pack
{
 "key": "profile-photo-check",
 "title": "Profile photo check",
 "tagline": "Are these photos one real adult's profile?",
 "description": "Checks a set of profile photos: enough of them, enough with a face, the same person from one face photo to the next, nothing written over the picture, no screenshots. It answers in codes, identifies no one and recognises no celebrity.",
 "version": "1.2.0",
 "tier": "WORKER",
 "kind": null,
 "distribution": "CLOUD",
 "regulatoryClass": "SENSITIVE",
 "category": null,
 "listed": false,
 "workers": [
  "orazaka-worker-photo"
 ],
 "controls": {
  "scopeGuard": true,
  "consent": false,
  "safety": false
 },
 "rulesets": [],
 "studios": [
  {
   "key": "profile-photo-check",
   "label": "Profile photo check",
   "summary": "Faces present, the same person throughout, nothing written on the photo.",
   "description": "Finds the faces in each photo, asks a vision model three yes/no questions about it, and gives a verdict made of codes. The face vectors live for one job and are never kept; a model answer that cannot be read blocks nothing.",
   "inputs": [
    {
     "key": "photos",
     "title": "Les photos (fichiers envoyés)"
    },
    {
     "key": "photosBase64",
     "title": "Les mêmes photos, en base64"
    },
    {
     "key": "compareFaces",
     "title": "Comparer les visages entre eux"
    },
    {
     "key": "minPhotos",
     "title": "Photos au moins"
    },
    {
     "key": "maxPhotos",
     "title": "Photos au plus"
    },
    {
     "key": "minFacePhotos",
     "title": "Photos de visage au moins"
    }
   ],
   "steps": 3,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 40
  }
 ]
}
```

### Trade Showcase

```pack
{
 "key": "trade-showcase",
 "title": "Trade Showcase",
 "tagline": null,
 "description": null,
 "version": "1.2.0",
 "tier": "DATA",
 "kind": null,
 "distribution": "OSS",
 "regulatoryClass": "STANDARD",
 "category": null,
 "listed": false,
 "workers": [],
 "controls": {
  "scopeGuard": false,
  "consent": false,
  "safety": false
 },
 "rulesets": [],
 "studios": [
  {
   "key": "trade-showcase",
   "label": "Trade Showcase",
   "summary": "Your job sites become a showcase that sells.",
   "description": "Drop in photos of your work: Orazaka describes them, then composes a branded showcase visual.",
   "inputs": [
    {
     "key": "photos",
     "title": "Photos de vos réalisations"
    },
    {
     "key": "trade",
     "title": "Votre métier"
    }
   ],
   "steps": 3,
   "stepKinds": [
    "CAPABILITY"
   ],
   "credits": 210
  }
 ]
}
```

Kept off the shelf (`catalog.status: DRAFT`): `echo-toolkit` — installable for the platform's tests, never sold.

## The reference packs (`orazaka-packs`)

| Pack                   | Version | Studios                                                                                                                                                                                                                                                                                                                          |
| :--------------------- | :------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `data-analytics`       | 1.6.0   | `data-driven-ui` · `video-understanding` · `creator-suggestions`                                                                                                                                                                                                                                                                 |
| `document-validation`  | 1.7.3   | `document-authenticity` · `compliance-check`                                                                                                                                                                                                                                                                                     |
| `echo-toolkit`         | 1.2.0   | `echo-reverse`                                                                                                                                                                                                                                                                                                                   |
| `media-safety`         | 1.2.0   | `media-safety`                                                                                                                                                                                                                                                                                                                   |
| `message-authenticity` | 1.3.0   | `message-authenticity`                                                                                                                                                                                                                                                                                                           |
| `orazaka-media`        | 1.13.0  | `image-generation` · `art-golden-hour` · `art-miniature` · `art-still-life` · `art-montreal-night` · `video-generation` · `cinematic-treatment` · `cinematic-critique` · `cinematic-script` · `cinematic-cut` · `video-with-sound` · `video-sound` · `speech-synthesis` · `image-analysis` · `audio-analysis` · `video-analysis` |
| `outbound-prospection` | 1.3.0   | `outbound-prospection` · `lead-research` · `followup-sequences`                                                                                                                                                                                                                                                                  |
| `profile-photo-check`  | 1.2.0   | `profile-photo-check`                                                                                                                                                                                                                                                                                                            |
| `realestate-studio`    | 1.3.0   | `realestate-reels`                                                                                                                                                                                                                                                                                                               |
| `trade-showcase`       | 1.2.0   | `trade-showcase`                                                                                                                                                                                                                                                                                                                 |
| `bien-etre`            | 1.6.0   | `guided-journaling` · `session-preparation` · `mood-tracking` · `session-summary`                                                                                                                                                                                                                                                |

## From the terminal

| Command                              | What it does                                                             |
| :----------------------------------- | :----------------------------------------------------------------------- |
| `orazaka pack validate <directory>`  | Check a bundle's manifest, and whether this platform can run it          |
| `orazaka pack install <directory>`   | Install a bundle: capabilities, billing and catalogue, or none of them   |
| `orazaka pack publish <directory>`   | Put an installed bundle's pack and Studios on the shelf                  |
| `orazaka pack list [directory]`      | List the bundles this deployment's pack sources offer                    |
| `orazaka studio list`                | Browse the Studio catalogue                                              |
| `orazaka studio installed`           | List the Studios you have installed                                      |
| `orazaka studio install <studioKey>` | Install a Studio into your workspace                                     |
| `orazaka studio run <target>`        | Run an installed Studio — or, with --studio, a toolkit Studio by its key |
| `orazaka studio logs <runId>`        | Show a run's per-step state and results                                  |

## Manifest fields

From `pack.schema.json` — the schema `orazaka pack validate` checks against.

| Field             | Required | Values                                 | Meaning                                                                     |
| :---------------- | :------- | :------------------------------------- | :-------------------------------------------------------------------------- |
| `apiVersion`      | yes      | —                                      | Manifest grammar version.                                                   |
| `key`             | yes      | —                                      | The bundle's stable identity.                                               |
| `version`         | yes      | —                                      | —                                                                           |
| `tier`            | —        | `DATA` · `CAPABILITY` · `WORKER`       | What the pack needs from the platform.                                      |
| `distribution`    | —        | `OSS` · `CLOUD` · `PARTNER`            | Where the pack may be obtained.                                             |
| `regulatoryClass` | —        | `STANDARD` · `SENSITIVE` · `REGULATED` | Drives the seven controls of phase I.                                       |
| `kind`            | —        | `VERTICAL` · `TOOLKIT`                 | How the pack reaches the user (ADR-061).                                    |
| `requires`        | —        | —                                      | What must already be true of the platform for this pack to run.             |
| `catalog`         | —        | —                                      | The shelf entry: a `pack` row, its category and its bundling of Studios.    |
| `pricing`         | —        | —                                      | What the pack costs and what buying it grants — the billing_pack half.      |
| `studios`         | yes      | —                                      | The Studios this bundle ships.                                              |
| `scopeGuard`      | —        | —                                      | The domain this pack REFUSES to answer in.                                  |
| `consent`         | —        | —                                      | The versioned consent a REGULATED pack requires before it may be installed. |
| `safety`          | —        | —                                      | A REGULATED pack's crisis handling.                                         |

## Writing a pack

A pack is a **directory**. It does not live in this repository, it is not compiled, and installing
one changes no code. If you can write YAML and JSON, you can ship a Studio.

This guide was written by installing a pack from outside the repository and writing down every
place it went wrong. Each **⚠** below is a mistake that actually happened, in the order it happened.

---

### 1. The shape

```
my-pack/
  pack.yaml                          # the manifest — the only required file
  i18n/
    en.yaml                          # one file per locale, discovered by convention
    fr.yaml
  studios/
    my-studio/
      blueprint.json                 # the workflow
  worker/                            # Tier-W only: your worker process
    worker.yaml
    …
```

Nothing here names a path inside Orazaka. Put the directory anywhere you like — `~/packs/my-pack`
is fine.

### 2. The manifest

```yaml
apiVersion: orazaka.dev/v1
key: my-pack # kebab-case, globally unique
version: 1.0.0 # semver
tier: DATA # DATA | CAPABILITY | WORKER
distribution: OSS # OSS | CLOUD | PARTNER
regulatoryClass: STANDARD # STANDARD | SENSITIVE | REGULATED

requires:
  capabilities: [] # empty for DATA: you reuse orazaka.core.*

catalog:
  categoryKey: business-compliance # business-compliance | creativity-media | wellbeing | data-analytics (closed list)
  iconKey: sparkles

pricing:
  priceCents: 0
  includedCredits: 0

studios:
  - key: my-studio
    profession: general
    iconKey: studio
    pricing: FREE # FREE | INCLUDED | PAID
    entitlementKey: studio.my-studio # by convention studio.<key>
    blueprint: studios/my-studio/blueprint.json
```

> ⚠ **`tier` is `DATA`, not `D`.** The tiers are spelled out: `DATA`, `CAPABILITY`, `WORKER`.
>
> ⚠ **`regulatoryClass` has no `NONE`.** The floor is `STANDARD`.
>
> ⚠ **`blueprint` is a path, not an inline object.** The manifest names files; it does not embed
> them.
>
> ⚠ **There is no `i18n:` key.** The `i18n/` directory is found by convention. The manifest rejects
> unknown properties outright, so an invented key fails validation rather than being ignored.
>
> ⚠ **`pricing` is `priceCents` + `includedCredits`.** Not `bundledCredits`. `priceCents` is in cents of the
> billing service's currency (`billing.currency` — Canadian dollars for Orazaka: `7900` is 79 $ CA); a manifest never
> names a currency.

Everything else is optional and has a default. `orazaka pack validate` names the exact line when
something is wrong, so run it before you run anything else.

#### 2.1 Declaring `SENSITIVE`

Raise `regulatoryClass` above `STANDARD` and the engine applies four controls to every run of your
pack. You do not implement them and you cannot opt out of them; you declare one thing, and the
platform owes you the rest.

```yaml
regulatoryClass: SENSITIVE

scopeGuard: # REQUIRED once regulatoryClass is not STANDARD
  refusedTerms: # the domain your pack does NOT serve
    - conseil juridique
    - avocat
  refusal: >- # what a refused turn is told, verbatim
    Je rédige et relis des documents. Je ne donne pas de conseil juridique —
    pour cela, adressez-vous à un professionnel du droit.
```

What you get:

| Control             | What the engine does                                                                                            | What you declare                                                                             |
| :------------------ | :-------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------- |
| Data class          | every run, input and artefact is stamped `SENSITIVE`; it is excluded from analytics and never used for training | nothing                                                                                      |
| Shortened retention | runs are purged after 30 days instead of the platform's 180                                                     | nothing; an installation may set `retentionDays` in its config to go **lower**, never higher |
| Audit log           | `RUN_STARTED` and a terminal row per run, append-only, `UPDATE` and `DELETE` refused by the database            | nothing                                                                                      |
| Scope guard         | a turn naming a refused term is stopped before any inference, and answered with your sentence                   | `scopeGuard`                                                                                 |

The publish gate is code, not a checklist: a pack that declares a regulatory class above `STANDARD`
without a `scopeGuard` **fails to install**, naming the missing key. A second condition is not yours
to satisfy: your pack is also refused by a **platform** that stores assets in the clear. That is the
platform operator's declaration, not yours, and the error names the key they must set. On a local
developer machine it is already satisfied. A pack that refuses nothing has
not stated a domain, and a legal-drafting Studio with no scope guard is one that answers legal
questions.

`refusedTerms` is matched on whole words, accent- and case-insensitively, against the turn as the
user wrote it. Name the _domain you decline_, not every phrasing of it — the list is a declaration,
not a filter, and the engine never adds terms of its own.

### 3. The blueprint

```json
{
  "version": "1.0.0",
  "status": "PUBLISHED",
  "estimatedCredits": 40,
  "createdBy": "you",
  "definition": {
    "studioKey": "my-studio",
    "version": "1.0.0",
    "steps": [
      {
        "id": "brief",
        "kind": "CAPABILITY",
        "featureKey": "orazaka.core.chat.completion",
        "dependsOn": [],
        "inputs": { "prompt": "Write about {{inputs.subject}}." },
        "out": "brief",
        "onError": "FAIL",
        "maxAttempts": 1,
        "timeout": "PT2M"
      }
    ],
    "outputs": [{ "key": "brief", "label": "Your brief", "from": "{{steps.brief.content}}", "type": "TEXT" }]
  },
  "inputSchema": {
    "type": "object",
    "required": ["subject"],
    "properties": { "subject": { "type": "string", "title": "Subject" } }
  },
  "configSchema": { "type": "object", "properties": {} }
}
```

> ⚠ **An output needs `label` and `type`, not just `key` and `from`.** A missing label is a `400`
> on the first run, not at install.

**Step kinds**: `CAPABILITY` (runs a model), `TRANSFORM` (reshapes values), `APPROVAL` (parks for a
human), `CONNECTOR` (calls out), `KNOWLEDGE`. **`onError`**: `FAIL` | `SKIP` | `RETRY` — and a
`SKIP` needs a written `skipRationale`, which a fitness function checks.

**Templates.** `{{inputs.x}}`, `{{config.x}}`, `{{steps.<out>}}`, `{{item}}` inside a `forEach`. A
template that is _exactly one placeholder_ over a list stays a list; anywhere else it renders as
text. That is what lets a step take a list of asset ids.

**Closed sets are chosen, never typed.** A field whose values are a known set is a list in every client
(web, phone), so a person cannot type a value that does not exist (`tests/closed-sets.test.mjs` refuses it):

| You write                                                  | The client draws                                                                                                                                                                                                                                     |
| :--------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `"enum": ["9:16", "16:9"]`                                 | a select (readable names come from the client's messages)                                                                                                                                                                                            |
| `"oneOf": [{ "const": "9:16", "title": "Vertical" }, …]`   | a select with your labels                                                                                                                                                                                                                            |
| `"x-options": { "source": "models", "category": "image" }` | a searchable picker fed by the model catalogue (`orazaka_models`): label, local or cloud, speed, typical time, price, availability and the default that will run. **Never list the models yourself** — a hand-kept `enum` drifts from the catalogue. |
| `"x-control": "seed"`                                      | a number with a « random » switch (nothing is sent while it is on)                                                                                                                                                                                   |
| `"x-advanced": true`                                       | folded under « More options » — model, seed and things to avoid are advanced; the field the Studio is about is not                                                                                                                                   |

**Every select speaks both languages (`x-i18n`).** A field that is a list carries its words in English and French, so a
client never shows the blueprint's French on an English page: `"x-i18n": {"en": {"title", "description", "options"},
"fr": {…}}`, where `options` maps each `enum` value to its label — or, on a catalogue-fed model field, each model id to
`{ "label": "LTX-Video · fast", "hint": "one line" }`. The helper is one line (**≤ 140 characters**), a hint ≤ 100; the
long comparison of the models is never written there: the client draws it from the catalogue's facts (time, price, where it
runs) and from `x-options.facts` (`{ "<model id>": ["1216×704", "5 s"] }`, language-neutral). The category of `x-options` is
the catalogue's, **lowercase** (`chat`, not `CHAT`: a category that matches no model draws an empty select).
`tests/closed-sets.test.mjs` refuses a select without its words, a long helper, or an uppercase category.

**Every field speaks both languages, not only the selects.** A free-text, numeric, boolean or file field carries
`x-i18n.en` and `x-i18n.fr` too: a `title`, a one-line `description` (**≤ 140 characters**) and, for a free-text field,
the `placeholder` it shows (an example of what to type). The blueprint's own `title` / `description` stay for clients
that predate it. The same test refuses a field without them, an English word that is French, and the dev data check
(`node scripts/audit-studio-forms.mjs` in krizaka/orazaka) reads the published blueprints for the same.

The Studio service writes the catalogue's list into the schema it serves (`oneOf` of `const`/`title`, the facts in
`x-meta`) and refuses, before any credit is held, a value that is not in it.

**`estimatedCredits` is a hold, not a price.** Size it for your blueprint's _worst legal input_ —
the maximum `forEach` fan-out, every optional step firing. It is released, not charged: a run
settles what it measured.

### 4. Install it

```bash
orazaka pack validate ~/packs/my-pack     # shape, then the platform's own checks
orazaka pack install  ~/packs/my-pack
```

`validate` checks your manifest against **the platform's** schema — the one the CLI carries, not
one you ship — and then asks the running platform whether every `featureKey` you name resolves.

> ⚠ **A published version is immutable.** Re-installing `1.0.0` after editing the blueprint does
> nothing and reports success. Bump the version. Editing means minting a version (ADR-034 §4).

### 5. Make it runnable

Installing puts a Studio in the catalogue; it does not entitle anyone to it. Your pack's
`entitlementKey` is granted to whoever holds the pack:

```bash
curl -X POST localhost:8088/api/v1/billing/pack-subscriptions/me/my-pack \
     -H "Authorization: Bearer $TOKEN"
```

> ⚠ **The entitlement cache is 60 seconds.** A pack subscription does not evict it today, so a
> Studio you just bought can stay `REQUIRES_PLAN` for up to a minute. Wait, do not re-install.

Plans are the platform's commercial offer, so bundling your Studio into `free`/`premium` is a
deployment decision, not something a pack can do to itself.

### 6. Tier-W: bringing your own worker

Declare it in the manifest —

```yaml
requires:
  capabilities:
    - key: orazaka.echo.text.reverse # your namespace, your key
      label: Reverse text
      routingKey: job.echo.reverse # job.<family>.<action>
      handlerKey: echo.reverse
      billableCapability: CHAT
      billableUnit: KILOTOKEN
  workers:
    - name: orazaka-worker-echo
      bindings: ["job.echo.*"]
```

— and write a process that honours [`docs/WORKER_PROTOCOL.md`](../WORKER_PROTOCOL.md). **The
broker is the plugin boundary**: your worker links no Orazaka library and implements no interface.
It must

1. **declare its own topology** — exchange, queue, bindings, DLQ — so installing your pack needs no
   change to the platform's broker configuration;
2. **decide from the routing key**, never from a capability name;
3. answer `job.{jobId}.done` or `job.{jobId}.error` on `orazaka.events`;
4. report **measurements** (`tokens`, `frames`, `gpuSeconds`, …), never units or prices;
5. be **idempotent by `messageId`** — delivery is at-least-once.

> ⚠ **The success key is `result`, not `output`.** Getting it wrong gives you a `SUCCEEDED` run
> with an empty output and no error anywhere.

The worked example is [`orazaka-packs/echo-toolkit`](https://github.com/krizaka/orazaka-packs/tree/main/echo-toolkit) — a manifest, a
Studio and a ~120-line Python worker that reverses a string. It is deliberately trivial: everything
in it that is not about the extension point would hide the extension point.

#### 6.1 Three things that would otherwise cost you an hour

Found by shipping `document-validation` (ADR-052 §6), and none of them visible from reading the code.

**The broker credentials are the `RABBITMQ_*` family** — `RABBITMQ_HOST`, `RABBITMQ_PORT`,
`RABBITMQ_USER`, `RABBITMQ_PASS`. They used to have two names each depending on how your `.env`
was generated, and picking the wrong one got you `ACCESS_REFUSED (403)` with no diagnosis; ADR-053
§8 settled it. The old `SPRING_RABBITMQ_*` and `RABBITMQ_PASSWORD` names are still read for one
version, with a warning naming what to rename — so an older `.env` keeps working and tells you.

**Re-installing at the same blueprint version keeps the stored one.** A `PUBLISHED` blueprint
version is immutable by design — shipping a change means shipping a new version. `pack install`
still says _"Installed"_, and your edit is not what runs. Bump `version` in the blueprint (and in
`definition.version`) every time you change it. The installer logs what it kept, so check the
studio service's log if a change seems to have no effect.

**`{{steps.out}}` is for prompts, `{{steps.out.field}}` is for data.** A whole-step reference
interpolates the value's `toString` — fine inside a prompt, useless to a JSON parser. If your step
must hand structured data to a later step, emit it as an explicit JSON string field and reference
that field.

### 7. Where packs come from

One property decides, and it is the only difference between an open-source deployment and a hosted
one:

```bash
ORAZAKA_PACKS_SOURCES=orazaka-packs                       # OSS: this repo's reference packs
ORAZAKA_PACKS_SOURCES=orazaka-packs,~/packs               # plus your own
ORAZAKA_PACKS_SOURCES=~/packs,registry:https://…          # plus a hosted registry
```

`orazaka pack list` shows the configured sources and what they offer. A registry source is listed
and marked unavailable on a local deployment rather than silently ignored.

### 8. Licence

Your pack is yours. See [docs/LICENSING.md](../LICENSING.md).
