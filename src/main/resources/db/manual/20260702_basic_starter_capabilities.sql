-- BASIC adopta las capacidades de STARTER con limites comerciales propios.
update subscription
set max_branches = 1,
    max_barbers = 2,
    max_admins = 1,
    ai_enabled = false,
    loyalty_enabled = true,
    promotions_enabled = false,
    custom_rewards_enabled = true
where upper(trim(coalesce(plan, ''))) = 'BASIC';
