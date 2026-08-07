// Caminhos relativos — build de produção assume backend servido na mesma origem
// (reverse proxy), então não precisa saber o domínio final em tempo de build.
export const environment = {
  production: true,
  apiUrl: '/api',
  authUrl: ''
};
