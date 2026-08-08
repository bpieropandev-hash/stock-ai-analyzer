import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  standalone: true,
  selector: 'app-recommendation-badge',
  imports: [CommonModule],
  template: `
    <span class="pill" [ngClass]="badgeClass" [class.large]="large">
      <span class="pill-dot"></span>
      {{ label }}
    </span>
  `,
  styles: [`
    .pill {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 10px;
      border-radius: 4px;
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      white-space: nowrap;
      border: 1px solid transparent;
    }

    .pill.large {
      font-size: 12px;
      padding: 7px 18px;
      border-radius: 6px;
      gap: 7px;
    }

    .pill-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: currentColor;
      opacity: 0.7;
      flex-shrink: 0;
    }

    .pill.large .pill-dot {
      width: 7px;
      height: 7px;
    }

    .buy {
      background: rgba(0, 229, 195, 0.12);
      color: var(--accent);
      border-color: rgba(0, 229, 195, 0.3);
    }

    .hold {
      background: rgba(59, 130, 246, 0.12);
      color: var(--blue);
      border-color: rgba(59, 130, 246, 0.3);
    }

    .wait {
      background: rgba(240, 160, 32, 0.12);
      color: var(--amber);
      border-color: rgba(240, 160, 32, 0.3);
    }

    .avoid {
      background: rgba(255, 59, 92, 0.12);
      color: var(--danger);
      border-color: rgba(255, 59, 92, 0.3);
    }

    .neutral {
      background: rgba(98, 122, 143, 0.08);
      color: var(--text-secondary);
      border-color: rgba(98, 122, 143, 0.2);
    }
  `]
})
export class RecommendationBadge {
  @Input() recommendation = '';
  @Input() large = false;

  get badgeClass(): string {
    const map: Record<string, string> = {
      'ATRATIVO':     'buy',
      'NEUTRO':       'hold',
      'CAUTELA':      'wait',
      'DESFAVORÁVEL': 'avoid',
      // Rótulos antigos — análises ainda cacheadas no Redis (TTL 30 min)
      'COMPRAR':      'buy',
      'COMPRAR_MAIS': 'buy',
      'MANTER':       'hold',
      'AGUARDAR':     'wait',
      'EVITAR':       'avoid',
      'VENDER':       'avoid',
    };
    return map[this.recommendation] ?? 'neutral';
  }

  get label(): string {
    const map: Record<string, string> = {
      'ATRATIVO':     'Atrativo',
      'NEUTRO':       'Neutro',
      'CAUTELA':      'Cautela',
      'DESFAVORÁVEL': 'Desfavorável',
      // Rótulos antigos exibem a linguagem descritiva nova (Res. CVM 20/2021)
      'COMPRAR':      'Atrativo',
      'COMPRAR_MAIS': 'Atrativo',
      'MANTER':       'Neutro',
      'AGUARDAR':     'Cautela',
      'EVITAR':       'Desfavorável',
      'VENDER':       'Desfavorável',
    };
    return map[this.recommendation] ?? this.recommendation;
  }
}
