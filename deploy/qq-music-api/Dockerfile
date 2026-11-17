FROM node:24-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
ENV PORT=3200 AUTO_OPEN_EXPLORER=false QQ_DISABLE_UPDATE_CHECK=true
CMD ["node", "node_modules/@yakult-green-tea/qq-music-api/dist/src/app.js"]
