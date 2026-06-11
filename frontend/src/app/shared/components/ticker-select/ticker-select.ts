import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export const TICKER_MAP: Record<string, string> = {
  PETR4: 'Petrobras',
  VALE3: 'Vale',
  ITUB4: 'Itaú Unibanco',
  BBDC4: 'Bradesco',
  WEGE3: 'WEG',
  MGLU3: 'Magazine Luiza',
  ABEV3: 'Ambev',
  B3SA3: 'B3',
  RENT3: 'Localiza',
  SUZB3: 'Suzano',
};

const TICKER_LIST = Object.entries(TICKER_MAP).map(([ticker, name]) => ({ ticker, name }));

@Component({
  standalone: true,
  selector: 'app-ticker-select',
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ts-root" [class.open]="isOpen">

      <!-- Multi chips -->
      @if (multi && values.length) {
        <div class="ts-chips">
          @for (v of values; track v) {
            <span class="ts-chip">
              {{ v }}
              <button class="ts-chip-x" type="button" (click)="removeValue(v)">×</button>
            </span>
          }
        </div>
      }

      <!-- Input field -->
      <div class="ts-field">
        <svg class="ts-search-icon" width="13" height="13" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input
          class="ts-input"
          [class.has-value]="!multi && !!value && !isOpen"
          [(ngModel)]="inputText"
          [placeholder]="effectivePlaceholder"
          (focus)="onFocus()"
          (blur)="onBlur()"
          (keydown)="onKey($event)"
        />
        <svg class="ts-chevron" [class.rotated]="isOpen" width="12" height="12" viewBox="0 0 24 24"
          fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>

      <!-- Dropdown -->
      @if (isOpen) {
        <div class="ts-dropdown" (mousedown)="$event.preventDefault()">
          @for (item of filteredList; track item.ticker) {
            <div class="ts-option"
              [class.selected]="isSelected(item.ticker)"
              [class.disabled]="multi && values.length >= max && !isSelected(item.ticker)"
              (mousedown)="pick(item.ticker, $event)">
              @if (multi) {
                <span class="ts-check" [class.on]="isSelected(item.ticker)">
                  @if (isSelected(item.ticker)) { ✓ }
                </span>
              } @else {
                <span class="ts-dot" [class.on]="isSelected(item.ticker)"></span>
              }
              <span class="ts-opt-ticker">{{ item.ticker }}</span>
              <span class="ts-opt-name">{{ item.name }}</span>
            </div>
          }
          @if (customCandidate) {
            <div class="ts-option ts-custom" (mousedown)="pickCustom($event)">
              <span class="ts-dot"></span>
              <span class="ts-opt-ticker">{{ customCandidate }}</span>
              <span class="ts-opt-hint">Ticker personalizado — ↵ ou clique</span>
            </div>
          }
          @if (!filteredList.length && !customCandidate) {
            <div class="ts-empty">Nenhum resultado para "{{ inputText }}"</div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host {
      --a: #00d4aa;
      --a-dim: rgba(0, 212, 170, 0.1);
      --surf: #0d1929;
      display: block;
    }

    .ts-root { position: relative; width: 100%; }

    /* ── Chips (multi mode) ── */
    .ts-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 5px;
      margin-bottom: 6px;
    }

    .ts-chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      background: var(--a-dim);
      border: 1px solid rgba(0, 212, 170, 0.25);
      color: var(--a);
      border-radius: 4px;
      padding: 3px 8px;
      font-family: var(--font-mono);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.06em;
    }

    .ts-chip-x {
      background: none;
      border: none;
      color: inherit;
      cursor: pointer;
      font-size: 14px;
      line-height: 1;
      opacity: 0.55;
      padding: 0;
      display: flex;
      align-items: center;
      transition: opacity 0.12s;
      &:hover { opacity: 1; }
    }

    /* ── Input field ── */
    .ts-field {
      position: relative;
      display: flex;
      align-items: center;
    }

    .ts-search-icon {
      position: absolute;
      left: 11px;
      color: var(--text-muted);
      pointer-events: none;
      z-index: 1;
    }

    .ts-input {
      width: 100%;
      background: var(--surf);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 6px;
      color: var(--text-primary);
      padding: 9px 34px 9px 32px;
      font-family: var(--font-body);
      font-size: 13px;
      outline: none;
      transition: border-color 0.18s, box-shadow 0.18s;

      &::placeholder { color: var(--text-muted); }
      &:focus { border-color: var(--a); box-shadow: 0 0 0 2px var(--a-dim); }

      &.has-value {
        font-family: var(--font-mono);
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--text-primary);
      }
    }

    .ts-chevron {
      position: absolute;
      right: 10px;
      color: var(--text-muted);
      pointer-events: none;
      transition: transform 0.18s;
      &.rotated { transform: rotate(180deg); }
    }

    /* ── Dropdown ── */
    .ts-dropdown {
      position: absolute;
      top: calc(100% + 4px);
      left: 0;
      right: 0;
      background: #0d1929;
      border: 1px solid rgba(255, 255, 255, 0.11);
      border-radius: 6px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.55);
      z-index: 300;
      max-height: 280px;
      overflow-y: auto;
    }

    .ts-option {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 9px 13px;
      cursor: pointer;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      transition: background 0.12s;

      &:last-child { border-bottom: none; }
      &:hover { background: rgba(0, 212, 170, 0.07); }
      &.selected { background: rgba(0, 212, 170, 0.1); }
      &.disabled { opacity: 0.35; cursor: default; &:hover { background: none; } }
    }

    .ts-check {
      width: 16px;
      height: 16px;
      border: 1px solid rgba(255, 255, 255, 0.18);
      border-radius: 3px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      font-size: 10px;
      font-weight: 700;
      transition: all 0.15s;

      &.on {
        background: var(--a);
        border-color: var(--a);
        color: #04100d;
      }
    }

    .ts-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      border: 1px solid rgba(255, 255, 255, 0.15);
      flex-shrink: 0;
      transition: all 0.15s;

      &.on {
        background: var(--a);
        border-color: var(--a);
        box-shadow: 0 0 6px rgba(0, 212, 170, 0.5);
      }
    }

    .ts-opt-ticker {
      font-family: var(--font-mono);
      font-size: 12px;
      font-weight: 700;
      color: var(--text-primary);
      letter-spacing: 0.05em;
      min-width: 46px;
    }

    .ts-opt-name {
      font-size: 12px;
      color: var(--text-muted);
      flex: 1;
    }

    .ts-opt-hint {
      font-size: 11px;
      color: rgba(0, 212, 170, 0.55);
      font-style: italic;
      flex: 1;
    }

    .ts-custom:hover { background: rgba(0, 212, 170, 0.06) !important; }

    .ts-empty {
      padding: 14px 13px;
      font-size: 12px;
      color: var(--text-muted);
      text-align: center;
    }
  `]
})
export class TickerSelect implements OnChanges {
  @Input() multi    = false;
  @Input() max      = 99;
  @Input() value    = '';
  @Output() valueChange  = new EventEmitter<string>();
  @Input() values: string[] = [];
  @Output() valuesChange = new EventEmitter<string[]>();
  @Input() placeholder = 'PETR4, VALE3, ITUB4...';
  @Output() enter = new EventEmitter<void>();

  inputText = '';
  isOpen    = false;

  readonly LIST = TICKER_LIST;

  constructor(private el: ElementRef) {}

  @HostListener('document:click', ['$event'])
  onDocumentClick(e: MouseEvent) {
    if (this.isOpen && !this.el.nativeElement.contains(e.target)) {
      this.isOpen = false;
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['value'] && !this.isOpen) {
      this.inputText = changes['value'].currentValue || '';
    }
  }

  get effectivePlaceholder(): string {
    if (!this.multi && this.value && !this.isOpen) return '';
    return this.placeholder;
  }

  get filteredList() {
    const q = this.inputText.trim().toUpperCase();
    if (!q) return this.LIST;
    return this.LIST.filter(t =>
      t.ticker.startsWith(q) || t.name.toUpperCase().includes(q)
    );
  }

  get customCandidate(): string {
    const q = this.inputText.trim().toUpperCase();
    if (q.length >= 3 && !this.LIST.some(t => t.ticker === q)) return q;
    return '';
  }

  isSelected(ticker: string): boolean {
    return this.multi ? this.values.includes(ticker) : this.value === ticker;
  }

  onFocus() {
    this.isOpen = true;
  }

  onBlur() {
    setTimeout(() => {
      this.isOpen = false;
      if (!this.multi) {
        const t = this.inputText.trim().toUpperCase();
        if (t) {
          this.value = t;
          this.valueChange.emit(t);
        } else {
          this.inputText = this.value;
        }
      }
    }, 180);
  }

  onKey(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      this.isOpen = false;
      return;
    }
    if (e.key === 'Enter') {
      const t = this.inputText.trim().toUpperCase();
      if (!t) return;
      if (this.multi) {
        const vals = [...this.values];
        if (!vals.includes(t) && vals.length < this.max) {
          vals.push(t);
          this.valuesChange.emit(vals);
          this.inputText = '';
        }
      } else {
        this.value = t;
        this.valueChange.emit(t);
        this.inputText = t;
        this.isOpen = false;
        this.enter.emit();
      }
    }
  }

  pick(ticker: string, e: Event) {
    e.preventDefault();
    if (this.multi) {
      const vals = [...this.values];
      const idx  = vals.indexOf(ticker);
      if (idx >= 0) vals.splice(idx, 1);
      else if (vals.length < this.max) vals.push(ticker);
      this.valuesChange.emit(vals);
      this.inputText = '';
    } else {
      this.value = ticker;
      this.valueChange.emit(ticker);
      this.inputText = ticker;
      this.isOpen = false;
      this.enter.emit();
    }
  }

  pickCustom(e: Event) {
    const t = this.inputText.trim().toUpperCase();
    if (t) this.pick(t, e);
  }

  removeValue(v: string) {
    this.valuesChange.emit(this.values.filter(x => x !== v));
  }
}
