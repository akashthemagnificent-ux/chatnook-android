// Free system-emoji picker (spec §3.6a/§4): no paid icon service. Inserted at the
// composer cursor or used as a reaction. Wrapped in a shadcn popover by callers.
import { t } from '../lib/i18n'

const EMOJIS = [
  '👍', '❤️', '😂', '🎉', '🔥', '😮', '😢', '👀',
  '🙌', '✅', '💜', '🚀', '👋', '😅', '🤔', '😍',
  '🥳', '👏', '💯', '🙏', '😎', '☕', '🍕', '🎮',
  '📎', '⭐', '🌙', '☀️', '⚡', '🐛', '🎨', '✌️',
]

export function EmojiPicker({ onSelect }: { onSelect: (emoji: string) => void }) {
  return (
    <div className="grid grid-cols-8 gap-0.5" role="listbox" aria-label={t('a11y.emoji')}>
      {EMOJIS.map((emoji) => (
        <button key={emoji} type="button" role="option" aria-selected={false}
          onClick={() => onSelect(emoji)}
          className="flex size-9 items-center justify-center rounded-md text-xl transition-transform hover:scale-110 hover:bg-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand">
          {emoji}
        </button>
      ))}
    </div>
  )
}
