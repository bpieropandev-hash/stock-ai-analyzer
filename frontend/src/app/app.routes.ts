import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then(m => m.LoginPage)
  },
  {
    path: 'auth/callback',
    loadComponent: () => import('./pages/auth-callback/auth-callback').then(m => m.AuthCallbackPage)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.DashboardPage),
    canActivate: [authGuard]
  },
  {
    path: 'analysis',
    loadComponent: () => import('./pages/analysis/analysis').then(m => m.AnalysisPage),
    canActivate: [authGuard]
  },
  {
    path: 'portfolio',
    loadComponent: () => import('./pages/portfolio/portfolio').then(m => m.PortfolioPage),
    canActivate: [authGuard]
  },
  {
    path: 'simulator',
    loadComponent: () => import('./pages/simulator/simulator').then(m => m.SimulatorPage),
    canActivate: [authGuard]
  },
  {
    path: 'compare',
    loadComponent: () => import('./pages/compare/compare').then(m => m.ComparePage),
    canActivate: [authGuard]
  },
  { path: '**', redirectTo: 'dashboard' }
];
