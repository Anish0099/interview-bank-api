-- Seed 50 popular companies (Indian IT services, Indian product, FAANG+, other) and 20 roles.
-- Aliases are lowercase; matcher normalises before lookup.

INSERT INTO companies (canonical_name, slug, aliases, industry, description) VALUES
  -- Indian IT services
  ('TCS', 'tcs', ARRAY['tata consultancy services','tata consultancy','tcs digital','tcs ninja'], 'IT Services', 'Tata Consultancy Services is India''s largest IT services company.'),
  ('Infosys', 'infosys', ARRAY['infy','infosys limited'], 'IT Services', 'Bengaluru-headquartered global consulting and IT services firm.'),
  ('Wipro', 'wipro', ARRAY['wipro limited','wipro technologies'], 'IT Services', 'Bengaluru-based IT services and consulting company.'),
  ('Cognizant', 'cognizant', ARRAY['cts','cognizant technology solutions'], 'IT Services', 'Global professional services company originally headquartered in Chennai.'),
  ('Accenture', 'accenture', ARRAY['accenture india','accenture solutions'], 'IT Services', 'Global consulting firm with a large India delivery footprint.'),
  ('HCL', 'hcl', ARRAY['hcltech','hcl technologies','hcl tech'], 'IT Services', 'Noida-based IT services company.'),
  ('Tech Mahindra', 'tech-mahindra', ARRAY['techm','tech-m'], 'IT Services', 'Part of the Mahindra Group; IT and BPO services.'),
  ('Capgemini', 'capgemini', ARRAY['capgemini india','cap'], 'IT Services', 'French multinational IT services and consulting company.'),
  ('LTIMindtree', 'ltimindtree', ARRAY['lti','mindtree','larsen and toubro infotech'], 'IT Services', 'Merged L&T Infotech + Mindtree entity.'),
  ('Mphasis', 'mphasis', ARRAY['mphasis limited'], 'IT Services', 'Bengaluru-based IT services company.'),

  -- Indian product / internet
  ('Flipkart', 'flipkart', ARRAY['flipkart internet','myntra parent'], 'E-commerce', 'Bengaluru-based e-commerce marketplace owned by Walmart.'),
  ('Razorpay', 'razorpay', ARRAY['razorpay software'], 'Fintech', 'Payments and financial infrastructure company.'),
  ('Zomato', 'zomato', ARRAY['eternal'], 'Food-tech', 'Food delivery and restaurant discovery.'),
  ('Swiggy', 'swiggy', ARRAY['bundl technologies'], 'Food-tech', 'On-demand food, groceries and delivery.'),
  ('Paytm', 'paytm', ARRAY['one97 communications','paytm payments'], 'Fintech', 'Payments, wallets, and financial services.'),
  ('PhonePe', 'phonepe', ARRAY['phone pe','phonepe private limited'], 'Fintech', 'UPI payments and financial services super-app.'),
  ('CRED', 'cred', ARRAY['dreamplug'], 'Fintech', 'Members-only credit card payments and rewards.'),
  ('Meesho', 'meesho', ARRAY['fashnear technologies'], 'E-commerce', 'Social commerce and reseller marketplace.'),
  ('Zerodha', 'zerodha', ARRAY['zerodha broking'], 'Fintech', 'India''s largest discount stockbroker.'),
  ('Groww', 'groww', ARRAY['nextbillion technology'], 'Fintech', 'Investment and stockbroking platform.'),
  ('Postman', 'postman', ARRAY['postman inc','postman labs'], 'DevTools', 'API development platform.'),
  ('Freshworks', 'freshworks', ARRAY['freshdesk'], 'SaaS', 'Customer engagement software.'),
  ('Zoho', 'zoho', ARRAY['zoho corporation'], 'SaaS', 'Chennai-based business software suite.'),
  ('Myntra', 'myntra', ARRAY['myntra designs'], 'E-commerce', 'Fashion e-commerce, Flipkart subsidiary.'),
  ('Nykaa', 'nykaa', ARRAY['fsn e-commerce'], 'E-commerce', 'Beauty and fashion e-commerce.'),
  ('BharatPe', 'bharatpe', ARRAY['bharat pe','resilient innovations'], 'Fintech', 'Merchant payments and lending.'),
  ('Ola', 'ola', ARRAY['ani technologies','olacabs','ola cabs'], 'Mobility', 'Ride-hailing platform.'),

  -- FAANG+
  ('Amazon', 'amazon', ARRAY['amazon india','amazon development centre','aws'], 'Big Tech', 'Global e-commerce, cloud and devices company.'),
  ('Google', 'google', ARRAY['alphabet','google india','google inc'], 'Big Tech', 'Search, cloud, Android and consumer devices.'),
  ('Microsoft', 'microsoft', ARRAY['microsoft india','msft','microsoft corporation'], 'Big Tech', 'Cloud, developer tools and productivity software.'),
  ('Meta', 'meta', ARRAY['facebook','fb','meta platforms'], 'Big Tech', 'Social platforms and AR/VR.'),
  ('Apple', 'apple', ARRAY['apple inc','apple india'], 'Big Tech', 'Consumer devices, chips and services.'),
  ('Netflix', 'netflix', ARRAY['netflix inc'], 'Big Tech', 'Streaming entertainment.'),

  -- Popular in India (product / global)
  ('Adobe', 'adobe', ARRAY['adobe systems','adobe inc'], 'SaaS', 'Creative and document software.'),
  ('Salesforce', 'salesforce', ARRAY['sfdc','salesforce.com'], 'SaaS', 'CRM and cloud software.'),
  ('Oracle', 'oracle', ARRAY['oracle india','oracle corporation'], 'Enterprise', 'Databases, cloud and enterprise applications.'),
  ('Uber', 'uber', ARRAY['uber india','uber technologies'], 'Mobility', 'Ride-hailing, delivery, freight.'),
  ('LinkedIn', 'linkedin', ARRAY['linkedin corporation'], 'Big Tech', 'Professional networking, part of Microsoft.'),
  ('Atlassian', 'atlassian', ARRAY['atlassian corporation'], 'SaaS', 'Team collaboration software.'),
  ('GitHub', 'github', ARRAY['github inc'], 'DevTools', 'Code hosting and collaboration platform.'),
  ('Stripe', 'stripe', ARRAY['stripe inc','stripe payments'], 'Fintech', 'Global payments infrastructure.'),
  ('Databricks', 'databricks', ARRAY['databricks inc'], 'Data', 'Lakehouse platform for data and AI.'),
  ('Snowflake', 'snowflake', ARRAY['snowflake inc','snowflake computing'], 'Data', 'Cloud data platform.'),
  ('Walmart', 'walmart', ARRAY['walmart global tech','walmart labs'], 'E-commerce', 'Retailer with a large India tech center.'),
  ('Intuit', 'intuit', ARRAY['intuit inc'], 'SaaS', 'Financial software (QuickBooks, TurboTax).'),
  ('SAP', 'sap', ARRAY['sap labs','sap se'], 'Enterprise', 'ERP and enterprise software.'),
  ('IBM', 'ibm', ARRAY['international business machines','ibm india'], 'Big Tech', 'Enterprise IT, cloud and consulting.'),
  ('Deloitte', 'deloitte', ARRAY['deloitte usi','deloitte consulting'], 'Consulting', 'Global professional services firm.'),
  ('EY', 'ey', ARRAY['ernst and young','ernst & young','ey global'], 'Consulting', 'Global professional services firm.'),
  ('Nvidia', 'nvidia', ARRAY['nvidia corporation'], 'Big Tech', 'GPUs, AI hardware and software.'),
  ('Goldman Sachs', 'goldman-sachs', ARRAY['gs','goldman']  , 'Finance', 'Global investment bank with a large tech presence in India.')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO roles (canonical_name, slug, aliases, category) VALUES
  ('SDE-1', 'sde-1', ARRAY['software engineer i','sde 1','sde1','sde level 1','swe i'], 'Engineering'),
  ('SDE-2', 'sde-2', ARRAY['software engineer ii','sde 2','sde2','sde level 2','swe ii'], 'Engineering'),
  ('SDE-3', 'sde-3', ARRAY['software engineer iii','sde 3','sde3','sde level 3','swe iii'], 'Engineering'),
  ('Senior SDE', 'senior-sde', ARRAY['sr sde','senior software engineer','sr. sde'], 'Engineering'),
  ('Staff Engineer', 'staff-engineer', ARRAY['staff software engineer','staff swe'], 'Engineering'),
  ('Backend Engineer', 'backend-engineer', ARRAY['backend developer','backend swe','server engineer'], 'Engineering'),
  ('Frontend Engineer', 'frontend-engineer', ARRAY['frontend developer','ui engineer','client engineer'], 'Engineering'),
  ('Fullstack Engineer', 'fullstack-engineer', ARRAY['full stack engineer','full-stack developer','fullstack developer'], 'Engineering'),
  ('Data Engineer', 'data-engineer', ARRAY['data platform engineer'], 'Data'),
  ('ML Engineer', 'ml-engineer', ARRAY['machine learning engineer','mle'], 'Data'),
  ('DevOps Engineer', 'devops-engineer', ARRAY['devops','platform engineer','infra engineer'], 'Engineering'),
  ('SRE', 'sre', ARRAY['site reliability engineer','sre i','sre ii'], 'Engineering'),
  ('Mobile Engineer', 'mobile-engineer', ARRAY['mobile developer','mobile swe'], 'Engineering'),
  ('Android Engineer', 'android-engineer', ARRAY['android developer'], 'Engineering'),
  ('iOS Engineer', 'ios-engineer', ARRAY['ios developer','swift engineer'], 'Engineering'),
  ('QA Engineer', 'qa-engineer', ARRAY['sdet','test engineer','qa','quality engineer'], 'Engineering'),
  ('Engineering Manager', 'engineering-manager', ARRAY['em','tech manager'], 'Management'),
  ('Tech Lead', 'tech-lead', ARRAY['technical lead','tl'], 'Engineering'),
  ('Intern', 'intern', ARRAY['software engineering intern','swe intern','summer intern'], 'Engineering'),
  ('Associate Engineer', 'associate-engineer', ARRAY['associate software engineer','ase','graduate engineer trainee','get'], 'Engineering')
ON CONFLICT (slug) DO NOTHING;
