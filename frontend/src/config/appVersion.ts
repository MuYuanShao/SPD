import release from '../../../package.json'

// Release versions are maintained explicitly in package.json; build dates do not change them.
export const APP_VERSION = `V${release.version.split('.').slice(0, 2).join('.')}`
