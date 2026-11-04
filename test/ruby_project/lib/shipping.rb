module Shipping
  def self.light_cost(weight)
    base = weight * 2
    base += 5 if weight > 10
    base += 3 if weight > 20
    surcharge = base / 10
    handling = surcharge + weight
    insured = handling * 3 + base
    total = insured - surcharge + handling
    final = total + base - handling
    final + surcharge
  end

  def self.heavy_cost(weight)
    base = weight * 2
    base += 5 if weight > 10
    base += 3 if weight > 20
    surcharge = base / 10
    handling = surcharge + weight
    insured = handling * 3 + base
    total = insured - surcharge + handling
    final = total + base - handling
    final + surcharge
  end
end
