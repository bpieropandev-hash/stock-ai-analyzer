import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-auth-callback',
  template: `
    <div class="callback-wrapper">
      <div class="loading-spinner"></div>
      <p>Autenticando...</p>
    </div>
  `,
  styles: [`
    .callback-wrapper {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      background: var(--bg-base);
      color: var(--text-secondary);
    }
  `]
})
export class AuthCallbackPage implements OnInit {
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private auth: AuthService
  ) {}

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token) {
      this.auth.saveToken(token);
      this.router.navigate(['/dashboard']);
    } else {
      this.router.navigate(['/login']);
    }
  }
}
