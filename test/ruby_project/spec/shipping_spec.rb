require_relative "../lib/shipping"

RSpec.describe Shipping do
  it "costs parcels alike" do
    expect(Shipping.light_cost(25)).to eq(Shipping.heavy_cost(25))
  end
end
