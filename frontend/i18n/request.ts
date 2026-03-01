import {getRequestConfig} from 'next-intl/server';

export default getRequestConfig(async ({ locale }) => {
  const localeStr = locale as string | undefined;
  const baseLocale = (localeStr && ['en', 'zh'].includes(localeStr)) ? localeStr : 'en';

  return {
    locale: baseLocale,
    messages: (await import(`../messages/${baseLocale}.json`)).default
  };
});
