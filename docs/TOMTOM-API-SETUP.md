# TomTom API Setup Guide

Countersteer uses the TomTom Traffic Flow API to provide traffic-aware routing. This is optional -- the app works fine without it, but routes won't account for live traffic conditions.

The free tier includes 2,500 requests per day, which is more than enough for personal use.

## Step 1: Create a TomTom Developer Account

1. Open your browser and go to **developer.tomtom.com**
2. Click **Sign up** or **Register**
3. Fill in the registration form with your email and a password
4. Verify your email address by clicking the link in the confirmation email
5. Log in to the TomTom developer portal

## Step 2: Get Your API Key

1. After logging in, go to the **Dashboard**
2. Navigate to **Keys**
3. Your default API key will be displayed -- it is a long alphanumeric string
4. Copy the key (click the copy icon next to it, or select and copy manually)

## Step 3: Enter the Key in Countersteer

1. Open the Countersteer app
2. If you are going through initial setup, paste the key when prompted on the API Key screen
3. If you already completed setup, go to **Settings** and paste the key in the TomTom API Key field
4. Tap **Validate key** to confirm it works

The app will test the key against the TomTom Traffic Flow API. If validation succeeds, you will see a green checkmark and traffic data will be used when generating routes.

## Troubleshooting

**"Invalid key" error:**
- Double-check that you copied the entire key with no extra spaces
- Make sure you copied the API key, not an OAuth token or client ID
- The key may take a few minutes to activate after creation

**No traffic data on routes:**
- Traffic data is only available in areas where TomTom has coverage
- Very rural or remote areas may not have traffic flow data
- The app will still generate routes; they just won't have traffic-adjusted ETAs

**Rate limiting:**
- The free tier allows 2,500 requests per day
- See the API usage estimates below for typical daily usage
- If you hit the limit, routes will still work -- they just won't include traffic adjustments

## Estimated API Usage Per Day

Countersteer uses the TomTom Traffic Flow API to sample traffic conditions along each route. Here's how usage breaks down:

| Action | TomTom Requests | Notes |
|--------|----------------|-------|
| Generate route options | ~24 | 12 sample points × 2 route requests (back-road + highway) |
| Generate Out & Back | ~24 | Same as above |

**Typical daily usage scenarios:**

| Scenario | Routes Generated | TomTom Requests | % of Free Tier |
|----------|-----------------|-----------------|----------------|
| Casual ride (1-2 routes) | 1-2 | 24-48 | 1-2% |
| Active day (5 routes) | 5 | ~120 | 5% |
| Heavy planning (15 routes) | 15 | ~360 | 14% |
| Extreme (50 routes) | 50 | ~1,200 | 48% |

**Other APIs used (no daily limits):**

| API | Requests Per Action | Daily Limit |
|-----|-------------------|-------------|
| Valhalla (FOSSGIS) | 2 per route generation | No hard limit (be respectful) |
| Nominatim | 1 per search keystroke (debounced to 800ms) | 1 req/sec policy |
| Overpass API | 1 per navigation start | Reasonable use |
| OpenFreeMap | Tile loads (map display) | Unlimited |

Even on a heavy usage day, you'll stay well within the TomTom free tier of 2,500 requests.

## API Key Security

Your API key is stored in Android's EncryptedSharedPreferences on your device. It is never sent anywhere other than directly to the TomTom API servers. The key is not included in backups.
