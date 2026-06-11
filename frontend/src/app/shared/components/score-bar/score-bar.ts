import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  standalone: true,
  selector: 'app-score-bar',
  imports: [CommonModule],
  template: `
    <div class="sb-item">
      <div class="sb-row">
        <span class="sb-label">{{ label }}</span>
        <div class="sb-track">
          <div class="sb-fill"
            [style.width.%]="score * 10"
            [style.background]="barColor"
            [style.box-shadow]="'2px 0 10px ' + barColorDim">
          </div>
        </div>
        <span class="sb-value" [style.color]="barColor">{{ score | number:'1.1-1' }}</span>
      </div>
      @if (explanation) {
        <p class="sb-explanation">{{ explanation }}</p>
      }
    </div>
  `,
  styles: [`
    .sb-item {
      padding: 11px 0;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);

      &:last-child { border-bottom: none; }
    }

    .sb-row {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    /* Fixed 130px label */
    .sb-label {
      width: 130px;
      flex-shrink: 0;
      font-size: 11px;
      font-weight: 600;
      color: var(--text-secondary);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      white-space: nowrap;
    }

    /* Bar: flex-grow fills everything between label and value */
    .sb-track {
      flex: 1;
      height: 8px;
      background: rgba(255, 255, 255, 0.055);
      border-radius: 4px;
      overflow: visible;
      min-width: 0;
    }

    .sb-fill {
      height: 100%;
      border-radius: 4px;
      transition: width 1s cubic-bezier(0.4, 0, 0.2, 1);
    }

    /* Fixed 40px numeric value */
    .sb-value {
      width: 40px;
      flex-shrink: 0;
      text-align: right;
      font-family: var(--font-mono);
      font-size: 14px;
      font-weight: 700;
    }

    /* Explanation below, aligned after the label */
    .sb-explanation {
      font-size: 12px;
      color: var(--text-muted);
      line-height: 1.6;
      margin-top: 6px;
      padding-left: calc(130px + 12px);
    }

    @media (max-width: 600px) {
      .sb-label { width: 100px; font-size: 10px; }
      .sb-explanation { padding-left: 0; }
    }
  `]
})
export class ScoreBar {
  @Input() label = '';
  @Input() score = 0;
  @Input() explanation = '';

  get barColor(): string {
    if (this.score >= 7) return '#00e5c3';
    if (this.score >= 5) return '#f0a020';
    return '#ff3b5c';
  }

  get barColorDim(): string {
    if (this.score >= 7) return 'rgba(0, 229, 195, 0.35)';
    if (this.score >= 5) return 'rgba(240, 160, 32, 0.35)';
    return 'rgba(255, 59, 92, 0.35)';
  }
}
